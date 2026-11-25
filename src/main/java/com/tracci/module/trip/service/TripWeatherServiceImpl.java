package com.tracci.module.trip.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tracci.common.context.LoginUserContext;
import com.tracci.common.exception.ForbiddenException;
import com.tracci.common.exception.NotFoundException;
import com.tracci.common.response.ErrorCode;
import com.tracci.integration.amap.AmapProperties;
import com.tracci.module.trip.dto.response.TripWeatherResponse;
import com.tracci.module.trip.entity.Trip;
import com.tracci.module.trip.enums.TripWeatherStatus;
import com.tracci.module.trip.enums.WeatherProvider;
import com.tracci.module.trip.repository.TripMapper;
import com.tracci.module.trip.enums.TripPermission;
import com.tracci.module.trip.weather.provider.TripWeatherForecast;
import com.tracci.module.trip.weather.provider.TripWeatherProvider;
import com.tracci.module.trip.weather.provider.WeatherProviderRouter;
import com.tracci.module.trip.weather.provider.WeatherProviderType;
import com.tracci.module.trip.weather.provider.WeatherQuery;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;

@Slf4j
@Service
public class TripWeatherServiceImpl implements TripWeatherService {

    private static final String CACHE_PREFIX = "trip:weather:";
    private static final String FORECAST_TYPE = "DAILY";
    private static final String DATE_TOO_FAR_MESSAGE = "距离出发还有一段时间，临近出行时将为你更新天气。";
    private static final String PAST_TRIP_MESSAGE = "这段旅程的历史天气暂不可查看。";
    private static final String LOCATION_MISSING_MESSAGE = "暂无目的地位置信息，无法获取天气。";
    private static final String PROVIDER_ERROR_MESSAGE = "暂时无法获取天气信息，请稍后再试。";

    private final TripMapper tripMapper;
    private final WeatherProviderRouter weatherProviderRouter;
    private final AmapProperties amapProperties;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final TripAccessService tripAccessService;
    private final Clock clock;

    @Autowired
    public TripWeatherServiceImpl(
            TripMapper tripMapper,
            WeatherProviderRouter weatherProviderRouter,
            AmapProperties amapProperties,
            StringRedisTemplate redis,
            ObjectMapper objectMapper,
            TripAccessService tripAccessService) {
        this(tripMapper, weatherProviderRouter, amapProperties, redis, objectMapper,
                tripAccessService, Clock.systemDefaultZone());
    }

    TripWeatherServiceImpl(
            TripMapper tripMapper,
            WeatherProviderRouter weatherProviderRouter,
            AmapProperties amapProperties,
            StringRedisTemplate redis,
            ObjectMapper objectMapper,
            TripAccessService tripAccessService,
            Clock clock) {
        this.tripMapper = tripMapper;
        this.weatherProviderRouter = weatherProviderRouter;
        this.amapProperties = amapProperties;
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.tripAccessService = tripAccessService;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public TripWeatherResponse getTripWeather(String tripId, String clientPlatform, String timeZone) {
        String userId = LoginUserContext.requireUserId();
        Trip trip = getExistingTrip(tripId);
        assertReadAccess(trip, userId);

        WeatherProviderType providerType = weatherProviderRouter.resolveProviderType(clientPlatform);
        LocalDate today = LocalDate.now(clock.withZone(resolveTimeZone(timeZone)));
        ForecastWindow window = resolveForecastWindow(trip, today);
        LocationQuery location = chooseLocation(trip);
        String cacheKey = cacheKey(providerType, trip.getId(), location, window);
        TripWeatherResponse cached = readCache(cacheKey);
        if (cached != null) {
            return cached;
        }

        TripWeatherResponse response = buildResponse(trip, location, today, window, providerType, clientPlatform);
        writeCache(cacheKey, response);
        return response;
    }

    private TripWeatherResponse buildResponse(
            Trip trip,
            LocationQuery location,
            LocalDate today,
            ForecastWindow window,
            WeatherProviderType providerType,
            String clientPlatform) {
        if (!window.valid()) {
            log.warn("Trip weather skipped because trip dates are invalid tripId={}", trip.getId());
            return providerError(trip.getDestinationName(), providerType);
        }
        if (trip.getEndDate().isBefore(today)) {
            return unavailable(
                    TripWeatherStatus.PAST_TRIP_NOT_SUPPORTED,
                    PAST_TRIP_MESSAGE,
                    trip.getDestinationName(),
                    providerType);
        }
        if (!hasWeatherLocation(location, providerType)) {
            return unavailable(
                    TripWeatherStatus.LOCATION_MISSING,
                    LOCATION_MISSING_MESSAGE,
                    trip.getDestinationName(),
                    providerType);
        }

        int maxSupportedDays = Math.max(amapProperties.getWeather().getMaxSupportedDays(), 1);
        if (ChronoUnit.DAYS.between(today, trip.getStartDate()) >= maxSupportedDays) {
            return unavailable(
                    TripWeatherStatus.DATE_TOO_FAR,
                    DATE_TOO_FAR_MESSAGE,
                    trip.getDestinationName(),
                    providerType);
        }

        TripWeatherProvider provider = weatherProviderRouter.provider(providerType);
        WeatherQuery query = new WeatherQuery(
                trip.getId(),
                location.latitude(),
                location.longitude(),
                location.city(),
                location.displayCity(),
                location.country(),
                maxForecastDays(),
                clientPlatform,
                window.queryStart(),
                window.queryEnd());

        try {
            TripWeatherForecast forecast = provider.getForecast(query);
            return toResponse(trip, forecast, today, providerType);
        } catch (Exception ex) {
            log.warn("Trip weather provider error tripId={} provider={} locationPresent={} reason={}",
                    trip.getId(), providerType, StringUtils.hasText(location.city()), ex.getMessage());
            return providerError(trip.getDestinationName(), providerType);
        }
    }

    private TripWeatherResponse toResponse(
            Trip trip,
            TripWeatherForecast forecast,
            LocalDate today,
            WeatherProviderType providerType) {
        WeatherProvider source = forecast.provider() != null ? forecast.provider() : toWeatherProvider(providerType);
        TripWeatherStatus status = forecast.status();
        boolean available = status == TripWeatherStatus.AVAILABLE;
        return new TripWeatherResponse(
                available,
                status.name(),
                source.name(),
                FORECAST_TYPE,
                forecast.message(),
                today,
                trip.getDestinationName(),
                forecast.daily() == null ? java.util.List.of() : forecast.daily(),
                available ? maxForecastDays() : null,
                forecast.location());
    }

    private WeatherProvider toWeatherProvider(WeatherProviderType providerType) {
        return switch (providerType) {
            case AMAP -> WeatherProvider.AMAP;
            case APPLE_CLIENT -> WeatherProvider.APPLE_CLIENT;
            case DISABLED -> WeatherProvider.DISABLED;
        };
    }

    private Trip getExistingTrip(String tripId) {
        Trip trip = tripMapper.selectById(tripId);
        if (trip == null) {
            throw new NotFoundException(ErrorCode.TRIP_NOT_FOUND);
        }
        return trip;
    }

    private void assertReadAccess(Trip trip, String userId) {
        tripAccessService.requirePermission(trip.getId(), userId, TripPermission.READ);
    }

    private ZoneId resolveTimeZone(String timeZone) {
        if (!StringUtils.hasText(timeZone)) {
            return clock.getZone();
        }
        try {
            return ZoneId.of(timeZone.trim());
        } catch (Exception ignored) {
            return clock.getZone();
        }
    }

    private LocationQuery chooseLocation(Trip trip) {
        BigDecimal latitude = trip.getDestinationLatitude();
        BigDecimal longitude = trip.getDestinationLongitude();
        String displayCity = resolveDisplayCity(trip);
        String amapCity = resolveAmapCityKey(trip);
        String cacheKey = resolveLocationCacheKey(trip, latitude, longitude, amapCity, displayCity);
        return new LocationQuery(amapCity, displayCity, cacheKey, latitude, longitude, trip.getDestinationCountry());
    }

    private String resolveDisplayCity(Trip trip) {
        if (StringUtils.hasText(trip.getDestinationCityName())) {
            return trip.getDestinationCityName().trim();
        }
        if (StringUtils.hasText(trip.getDestinationName())) {
            return trip.getDestinationName().trim();
        }
        return null;
    }

    private String resolveAmapCityKey(Trip trip) {
        if (StringUtils.hasText(trip.getDestinationAdcode())) {
            return trip.getDestinationAdcode().trim();
        }
        if (StringUtils.hasText(trip.getDestinationCityCode())) {
            return trip.getDestinationCityCode().trim();
        }
        if (StringUtils.hasText(trip.getDestinationCityName())) {
            return trip.getDestinationCityName().trim();
        }
        return null;
    }

    private String resolveLocationCacheKey(
            Trip trip,
            BigDecimal latitude,
            BigDecimal longitude,
            String amapCity,
            String displayCity) {
        if (StringUtils.hasText(amapCity)) {
            return amapCity;
        }
        if (latitude != null && longitude != null) {
            return latitude + "," + longitude;
        }
        if (StringUtils.hasText(displayCity)) {
            return displayCity;
        }
        return "missing:" + trip.getId();
    }

    private boolean hasWeatherLocation(LocationQuery location, WeatherProviderType providerType) {
        if (providerType == WeatherProviderType.APPLE_CLIENT) {
            return location.latitude() != null && location.longitude() != null;
        }
        return StringUtils.hasText(location.city());
    }

    private ForecastWindow resolveForecastWindow(Trip trip, LocalDate today) {
        LocalDate startDate = trip.getStartDate();
        LocalDate endDate = trip.getEndDate();
        if (startDate == null || endDate == null || endDate.isBefore(startDate)) {
            return new ForecastWindow(null, null, "invalid-start", "invalid-end", false);
        }
        int maxSupportedDays = Math.max(amapProperties.getWeather().getMaxSupportedDays(), 1);
        LocalDate queryStart = startDate.isAfter(today) ? startDate : today;
        LocalDate providerEnd = today.plusDays(maxSupportedDays - 1L);
        LocalDate queryEnd = minDate(endDate, providerEnd);
        return new ForecastWindow(queryStart, queryEnd, queryStart.toString(), queryEnd.toString(), true);
    }

    private TripWeatherResponse unavailable(
            TripWeatherStatus status,
            String message,
            String locationName,
            WeatherProviderType providerType) {
        return new TripWeatherResponse(
                false,
                status.name(),
                toWeatherProvider(providerType).name(),
                FORECAST_TYPE,
                message,
                LocalDate.now(clock),
                locationName,
                java.util.List.of(),
                null);
    }

    private TripWeatherResponse providerError(String locationName, WeatherProviderType providerType) {
        return unavailable(TripWeatherStatus.PROVIDER_ERROR, PROVIDER_ERROR_MESSAGE, locationName, providerType);
    }

    private String cacheKey(
            WeatherProviderType providerType,
            String tripId,
            LocationQuery location,
            ForecastWindow window) {
        return CACHE_PREFIX
                + providerType.name().toLowerCase() + ":"
                + tripId + ":"
                + location.cacheKey() + ":"
                + (location.latitude() == null ? "" : location.latitude()) + ":"
                + (location.longitude() == null ? "" : location.longitude()) + ":"
                + window.cacheStart() + ":"
                + window.cacheEnd();
    }

    private TripWeatherResponse readCache(String cacheKey) {
        try {
            String raw = redis.opsForValue().get(cacheKey);
            if (!StringUtils.hasText(raw)) {
                return null;
            }
            return objectMapper.readValue(raw, TripWeatherResponse.class);
        } catch (Exception ex) {
            log.debug("Read weather cache failed key={}: {}", cacheKey, ex.getMessage());
            return null;
        }
    }

    private void writeCache(String cacheKey, TripWeatherResponse response) {
        try {
            redis.opsForValue().set(cacheKey, objectMapper.writeValueAsString(response), cacheTtl(response));
        } catch (JsonProcessingException ex) {
            log.debug("Serialize weather cache failed key={}: {}", cacheKey, ex.getMessage());
        } catch (RuntimeException ex) {
            log.debug("Write weather cache failed key={}: {}", cacheKey, ex.getMessage());
        }
    }

    private Duration cacheTtl(TripWeatherResponse response) {
        TripWeatherStatus status = TripWeatherStatus.valueOf(response.status());
        int minutes = switch (status) {
            case AVAILABLE -> amapProperties.getWeather().getCacheTtlMinutes();
            case CLIENT_REQUIRED -> amapProperties.getWeather().getCacheTtlMinutes();
            case DATE_TOO_FAR -> Math.min(
                    amapProperties.getWeather().getUnavailableCacheTtlMinutes(),
                    minutesUntilNextLocalDate());
            case PAST_TRIP_NOT_SUPPORTED -> amapProperties.getWeather().getUnavailableCacheTtlMinutes();
            case LOCATION_MISSING -> Math.min(amapProperties.getWeather().getCacheTtlMinutes(), 30);
            case PROVIDER_ERROR, PROVIDER_DISABLED -> amapProperties.getWeather().getProviderErrorCacheTtlMinutes();
        };
        return Duration.ofMinutes(Math.max(minutes, 1));
    }

    private int minutesUntilNextLocalDate() {
        ZonedDateTime now = ZonedDateTime.now(clock);
        ZonedDateTime nextDate = now.toLocalDate().plusDays(1).atStartOfDay(now.getZone());
        long minutes = Duration.between(now, nextDate).toMinutes();
        return Math.toIntExact(Math.max(minutes, 1));
    }

    private LocalDate minDate(LocalDate left, LocalDate right) {
        return left.isBefore(right) ? left : right;
    }

    private int maxForecastDays() {
        return Math.max(amapProperties.getWeather().getMaxForecastDays(), 1);
    }

    private record LocationQuery(
            String city,
            String displayCity,
            String cacheKey,
            BigDecimal latitude,
            BigDecimal longitude,
            String country) {
    }

    private record ForecastWindow(
            LocalDate queryStart,
            LocalDate queryEnd,
            String cacheStart,
            String cacheEnd,
            boolean valid) {
    }
}
