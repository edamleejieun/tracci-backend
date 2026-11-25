package com.tracci.module.trip.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tracci.common.context.LoginUserContext;
import com.tracci.common.exception.ForbiddenException;
import com.tracci.integration.amap.AmapProperties;
import com.tracci.module.trip.dto.response.TripWeatherDailyItem;
import com.tracci.module.trip.dto.response.TripWeatherLocationResp;
import com.tracci.module.trip.entity.Trip;
import com.tracci.module.trip.entity.TripMember;
import com.tracci.module.trip.enums.TripWeatherStatus;
import com.tracci.module.trip.enums.TripPermission;
import com.tracci.module.trip.enums.WeatherProvider;
import com.tracci.module.trip.repository.TripMapper;
import com.tracci.module.trip.weather.provider.TripWeatherForecast;
import com.tracci.module.trip.weather.provider.TripWeatherProvider;
import com.tracci.module.trip.weather.provider.WeatherProviderRouter;
import com.tracci.module.trip.weather.provider.WeatherProviderType;
import com.tracci.module.trip.weather.provider.WeatherQuery;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static com.tracci.testsupport.TripAccessTestSupport.stubOwnerAccess;

@ExtendWith(MockitoExtension.class)
class TripWeatherServiceImplTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-05-22T00:00:00Z"), ZoneId.of("Asia/Shanghai"));

    @Mock
    private TripMapper tripMapper;

    @Mock
    private WeatherProviderRouter weatherProviderRouter;

    @Mock
    private TripWeatherProvider amapWeatherProvider;

    @Mock
    private TripWeatherProvider appleClientWeatherProvider;

    @Mock
    private StringRedisTemplate redis;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private TripAccessService tripAccessService;

    private AmapProperties properties;
    private TripWeatherServiceImpl service;

    @BeforeEach
    void setUp() {
        properties = new AmapProperties();
        properties.setEnabled(true);
        properties.setWebServiceKey("test-key");
        properties.getWeather().setMaxForecastDays(4);
        properties.getWeather().setMaxSupportedDays(4);
        properties.getWeather().setCacheTtlMinutes(60);
        properties.getWeather().setUnavailableCacheTtlMinutes(360);
        properties.getWeather().setProviderErrorCacheTtlMinutes(3);
        lenient().when(redis.opsForValue()).thenReturn(valueOperations);
        lenient().when(weatherProviderRouter.resolveProviderType(null)).thenReturn(WeatherProviderType.AMAP);
        lenient().when(weatherProviderRouter.resolveProviderType("WECHAT_MINI")).thenReturn(WeatherProviderType.AMAP);
        lenient().when(weatherProviderRouter.resolveProviderType("IOS")).thenReturn(WeatherProviderType.APPLE_CLIENT);
        lenient().when(weatherProviderRouter.provider(WeatherProviderType.AMAP)).thenReturn(amapWeatherProvider);
        lenient().when(weatherProviderRouter.provider(WeatherProviderType.APPLE_CLIENT)).thenReturn(appleClientWeatherProvider);
        service = new TripWeatherServiceImpl(
                tripMapper,
                weatherProviderRouter,
                properties,
                redis,
                new ObjectMapper().findAndRegisterModules(),
                tripAccessService,
                CLOCK);
        stubOwnerAccess(tripAccessService, tripMapper);
        LoginUserContext.setUserId("u-1");
    }

    @AfterEach
    void tearDown() {
        LoginUserContext.clear();
    }

    @Test
    void getTripWeather_defaultRequestUsesAmapProvider() {
        Trip trip = trip("trip-1", "u-1", LocalDate.of(2026, 5, 23), LocalDate.of(2026, 5, 24));
        trip.setDestinationAdcode("330106");
        when(tripMapper.selectById("trip-1")).thenReturn(trip);
        when(valueOperations.get(anyString())).thenReturn(null);
        when(amapWeatherProvider.getForecast(any())).thenReturn(new TripWeatherForecast(
                TripWeatherStatus.AVAILABLE,
                WeatherProvider.AMAP,
                null,
                List.of(
                        dailyItem(LocalDate.of(2026, 5, 23), "cloudy", "天气舒适，适合城市漫步"),
                        dailyItem(LocalDate.of(2026, 5, 24), "sunny", "气温较高，注意防晒和补水")
                ),
                null));

        var resp = service.getTripWeather("trip-1", null);

        assertEquals(Boolean.TRUE, resp.available());
        assertEquals("AVAILABLE", resp.status());
        assertEquals("AMAP", resp.source());
        assertEquals(2, resp.items().size());
        verify(amapWeatherProvider).getForecast(any());
    }

    @Test
    void getTripWeather_iosRequestReturnsClientRequiredWithoutCallingAmap() {
        Trip trip = trip("trip-1", "u-1", LocalDate.of(2026, 5, 22), LocalDate.of(2026, 5, 22));
        trip.setDestinationCityName("杭州市");
        trip.setDestinationLatitude(new java.math.BigDecimal("30.2420000"));
        trip.setDestinationLongitude(new java.math.BigDecimal("120.1410000"));
        trip.setDestinationCountry("中国");
        when(tripMapper.selectById("trip-1")).thenReturn(trip);
        when(valueOperations.get(anyString())).thenReturn(null);
        when(appleClientWeatherProvider.getForecast(any())).thenReturn(new TripWeatherForecast(
                TripWeatherStatus.CLIENT_REQUIRED,
                WeatherProvider.APPLE_CLIENT,
                "请使用客户端 WeatherKit 查询目的地天气。",
                List.of(),
                new TripWeatherLocationResp("杭州市", new java.math.BigDecimal("30.2420000"),
                        new java.math.BigDecimal("120.1410000"), "中国")));

        var resp = service.getTripWeather("trip-1", "IOS");

        assertEquals(Boolean.FALSE, resp.available());
        assertEquals("CLIENT_REQUIRED", resp.status());
        assertEquals("APPLE_CLIENT", resp.source());
        assertEquals("杭州市", resp.location().city());
        assertEquals(new java.math.BigDecimal("30.2420000"), resp.location().latitude());
        assertEquals(new java.math.BigDecimal("120.1410000"), resp.location().longitude());
        verify(amapWeatherProvider, never()).getForecast(any());
        verify(appleClientWeatherProvider).getForecast(any());
    }

    @Test
    void getTripWeather_iosRequestReturnsLocationMissingWithoutCoordinates() {
        Trip trip = trip("trip-1", "u-1", LocalDate.of(2026, 5, 22), LocalDate.of(2026, 5, 22));
        trip.setDestinationCityName("杭州市");
        when(tripMapper.selectById("trip-1")).thenReturn(trip);
        when(valueOperations.get(anyString())).thenReturn(null);

        var resp = service.getTripWeather("trip-1", "IOS");

        assertEquals("LOCATION_MISSING", resp.status());
        verify(appleClientWeatherProvider, never()).getForecast(any());
        verify(amapWeatherProvider, never()).getForecast(any());
    }

    @Test
    void getTripWeather_returnsLocationMissing() {
        Trip trip = trip("trip-1", "u-1", LocalDate.of(2026, 5, 22), LocalDate.of(2026, 5, 22));
        when(tripMapper.selectById("trip-1")).thenReturn(trip);
        when(valueOperations.get(anyString())).thenReturn(null);

        var resp = service.getTripWeather("trip-1", null);

        assertEquals("LOCATION_MISSING", resp.status());
        verify(amapWeatherProvider, never()).getForecast(any());
    }

    @Test
    void getTripWeather_returnsPastTripNotSupported() {
        Trip trip = trip("trip-1", "u-1", LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 10));
        trip.setDestinationAdcode("330106");
        when(tripMapper.selectById("trip-1")).thenReturn(trip);
        when(valueOperations.get(anyString())).thenReturn(null);

        var resp = service.getTripWeather("trip-1", null);

        assertEquals("PAST_TRIP_NOT_SUPPORTED", resp.status());
        verify(amapWeatherProvider, never()).getForecast(any());
    }

    @Test
    void getTripWeather_returnsDateTooFar() {
        Trip trip = trip("trip-1", "u-1", LocalDate.of(2026, 5, 26), LocalDate.of(2026, 5, 28));
        trip.setDestinationAdcode("330106");
        when(tripMapper.selectById("trip-1")).thenReturn(trip);
        when(valueOperations.get(anyString())).thenReturn(null);

        var resp = service.getTripWeather("trip-1", null);

        assertEquals("DATE_TOO_FAR", resp.status());
        verify(amapWeatherProvider, never()).getForecast(any());
    }

    @Test
    void getTripWeather_mapsProviderErrorWhenForecastEmpty() {
        Trip trip = trip("trip-1", "u-1", LocalDate.of(2026, 5, 22), LocalDate.of(2026, 5, 22));
        trip.setDestinationAdcode("330106");
        when(tripMapper.selectById("trip-1")).thenReturn(trip);
        when(valueOperations.get(anyString())).thenReturn(null);
        when(amapWeatherProvider.getForecast(any())).thenReturn(new TripWeatherForecast(
                TripWeatherStatus.PROVIDER_ERROR,
                WeatherProvider.AMAP,
                "暂时无法获取天气信息，请稍后再试。",
                List.of(),
                null));

        var resp = service.getTripWeather("trip-1", null);

        assertEquals("PROVIDER_ERROR", resp.status());
    }

    @Test
    void getTripWeather_rejectsOtherUserTrip() {
        Trip trip = trip("trip-1", "u-2", LocalDate.of(2026, 5, 22), LocalDate.of(2026, 5, 22));
        trip.setDestinationAdcode("330106");
        when(tripMapper.selectById("trip-1")).thenReturn(trip);

        assertThrows(ForbiddenException.class, () -> service.getTripWeather("trip-1", null));
        verify(amapWeatherProvider, never()).getForecast(any());
    }

    @Test
    void getTripWeather_allowsActiveTripMember() {
        Trip trip = trip("trip-1", "u-2", LocalDate.of(2026, 5, 22), LocalDate.of(2026, 5, 22));
        trip.setDestinationAdcode("330106");
        when(tripMapper.selectById("trip-1")).thenReturn(trip);
        when(valueOperations.get(anyString())).thenReturn(null);
        when(amapWeatherProvider.getForecast(any())).thenReturn(new TripWeatherForecast(
                TripWeatherStatus.AVAILABLE, WeatherProvider.AMAP, null, List.of(), null));
        TripWeatherServiceImpl memberService = new TripWeatherServiceImpl(
                tripMapper, weatherProviderRouter, properties, redis,
                new ObjectMapper().findAndRegisterModules(), tripAccessService, CLOCK);
        TripMember editor = new TripMember();
        editor.setRole("editor");
        doReturn(new TripAccessService.TripAccess(trip, editor))
                .when(tripAccessService)
                .requirePermission("trip-1", "u-1", TripPermission.READ);

        var response = memberService.getTripWeather("trip-1", null, "Asia/Tokyo");

        assertEquals("AVAILABLE", response.status());
        verify(tripAccessService).requirePermission("trip-1", "u-1", TripPermission.READ);
    }

    @Test
    void getTripWeather_cacheHitSkipsProvider() throws Exception {
        Trip trip = trip("trip-1", "u-1", LocalDate.of(2026, 5, 22), LocalDate.of(2026, 5, 22));
        trip.setDestinationAdcode("330106");
        when(tripMapper.selectById("trip-1")).thenReturn(trip);
        String cached = new ObjectMapper().findAndRegisterModules().writeValueAsString(
                new com.tracci.module.trip.dto.response.TripWeatherResponse(
                        true, "AVAILABLE", "AMAP", "DAILY", null, LocalDate.of(2026, 5, 22),
                        "西湖", List.of(), 4)
        );
        when(valueOperations.get(anyString())).thenReturn(cached);

        var resp = service.getTripWeather("trip-1", null);

        assertEquals("AVAILABLE", resp.status());
        verify(amapWeatherProvider, never()).getForecast(any());
    }

    @Test
    void getTripWeather_cacheKeyContainsProvider() {
        Trip trip = trip("trip-1", "u-1", LocalDate.of(2026, 5, 20), LocalDate.of(2026, 5, 25));
        trip.setDestinationAdcode("330106");
        when(tripMapper.selectById("trip-1")).thenReturn(trip);
        when(valueOperations.get(anyString())).thenReturn(null);
        when(amapWeatherProvider.getForecast(any())).thenReturn(new TripWeatherForecast(
                TripWeatherStatus.AVAILABLE,
                WeatherProvider.AMAP,
                null,
                List.of(
                        dailyItem(LocalDate.of(2026, 5, 22), "cloudy", null),
                        dailyItem(LocalDate.of(2026, 5, 23), "overcast", null)
                ),
                null));

        service.getTripWeather("trip-1", null);

        verify(valueOperations).get("trip:weather:amap:trip-1:330106:::2026-05-22:2026-05-25");
    }

    @Test
    void getTripWeather_passesCityToProviderQuery() {
        Trip trip = trip("trip-1", "u-1", LocalDate.of(2026, 5, 22), LocalDate.of(2026, 5, 22));
        trip.setDestinationCityName("杭州市");
        when(tripMapper.selectById("trip-1")).thenReturn(trip);
        when(valueOperations.get(anyString())).thenReturn(null);
        when(amapWeatherProvider.getForecast(any())).thenReturn(new TripWeatherForecast(
                TripWeatherStatus.AVAILABLE,
                WeatherProvider.AMAP,
                null,
                List.of(dailyItem(LocalDate.of(2026, 5, 22), "sunny", null)),
                null));

        service.getTripWeather("trip-1", null);

        ArgumentCaptor<WeatherQuery> queryCaptor = ArgumentCaptor.forClass(WeatherQuery.class);
        verify(amapWeatherProvider).getForecast(queryCaptor.capture());
        assertEquals("杭州市", queryCaptor.getValue().city());
    }

    @Test
    void getTripWeather_dateTooFarCacheTtlDoesNotCrossLocalDateBoundary() {
        Clock nearMidnight = Clock.fixed(Instant.parse("2026-05-22T15:30:00Z"), ZoneId.of("Asia/Shanghai"));
        service = new TripWeatherServiceImpl(
                tripMapper,
                weatherProviderRouter,
                properties,
                redis,
                new ObjectMapper().findAndRegisterModules(),
                tripAccessService,
                nearMidnight);
        Trip trip = trip("trip-1", "u-1", LocalDate.of(2026, 5, 26), LocalDate.of(2026, 5, 28));
        trip.setDestinationAdcode("330106");
        when(tripMapper.selectById("trip-1")).thenReturn(trip);
        when(valueOperations.get(anyString())).thenReturn(null);
        ArgumentCaptor<Duration> ttlCaptor = ArgumentCaptor.forClass(Duration.class);

        var resp = service.getTripWeather("trip-1", null);

        assertEquals("DATE_TOO_FAR", resp.status());
        verify(valueOperations).set(
                eq("trip:weather:amap:trip-1:330106:::2026-05-26:2026-05-25"),
                anyString(),
                ttlCaptor.capture());
        assertEquals(Duration.ofMinutes(30), ttlCaptor.getValue());
    }

    private Trip trip(String tripId, String userId, LocalDate startDate, LocalDate endDate) {
        Trip trip = new Trip();
        trip.setId(tripId);
        trip.setUserId(userId);
        trip.setTitle("杭州周末");
        trip.setDestinationName("西湖风景名胜区");
        trip.setStartDate(startDate);
        trip.setEndDate(endDate);
        return trip;
    }

    private TripWeatherDailyItem dailyItem(LocalDate date, String icon, String tip) {
        return new TripWeatherDailyItem(date, "周一", "晴", 20, 28, "东", icon, null, tip);
    }
}
