package com.tracci.module.trip.weather.provider;

import com.tracci.integration.amap.AmapProperties;
import com.tracci.integration.amap.AmapWeatherClient;
import com.tracci.integration.amap.dto.AmapCastDTO;
import com.tracci.integration.amap.dto.AmapForecastDTO;
import com.tracci.integration.amap.dto.AmapWeatherResponse;
import com.tracci.module.trip.dto.response.TripWeatherDailyItem;
import com.tracci.module.trip.enums.TripWeatherStatus;
import com.tracci.module.trip.enums.WeatherProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Slf4j
@Component
@RequiredArgsConstructor
public class AmapWeatherProvider implements TripWeatherProvider {

    private final AmapWeatherClient amapWeatherClient;
    private final AmapProperties amapProperties;

    @Override
    public WeatherProviderType providerType() {
        return WeatherProviderType.AMAP;
    }

    @Override
    public TripWeatherForecast getForecast(WeatherQuery query) {
        AmapWeatherResponse response = amapWeatherClient.forecast(query.city());
        List<TripWeatherDailyItem> items = mapDailyItems(response, query);
        if (items.isEmpty()) {
            log.warn("Amap weather returned empty forecast items tripId={} cityPresent={}",
                    query.tripId(), StringUtils.hasText(query.city()));
            return new TripWeatherForecast(
                    TripWeatherStatus.PROVIDER_ERROR,
                    WeatherProvider.AMAP,
                    "暂时无法获取天气信息，请稍后再试。",
                    List.of(),
                    null);
        }
        return new TripWeatherForecast(
                TripWeatherStatus.AVAILABLE,
                WeatherProvider.AMAP,
                null,
                items,
                null);
    }

    private List<TripWeatherDailyItem> mapDailyItems(AmapWeatherResponse response, WeatherQuery query) {
        if (response == null || response.forecasts() == null || response.forecasts().isEmpty()) {
            return List.of();
        }
        int limit = Math.max(amapProperties.getWeather().getMaxForecastDays(), 1);

        return response.forecasts().stream()
                .filter(Objects::nonNull)
                .map(AmapForecastDTO::casts)
                .filter(Objects::nonNull)
                .flatMap(List::stream)
                .map(this::toDailyItem)
                .filter(Objects::nonNull)
                .filter(item -> !item.date().isBefore(query.queryStart()) && !item.date().isAfter(query.queryEnd()))
                .sorted(Comparator.comparing(TripWeatherDailyItem::date))
                .limit(limit)
                .toList();
    }

    private TripWeatherDailyItem toDailyItem(AmapCastDTO cast) {
        LocalDate date = parseDate(cast.date());
        if (date == null) {
            return null;
        }
        String weather = weatherText(cast.dayweather(), cast.nightweather());
        Integer tempMax = parseInteger(cast.daytemp());
        Integer tempMin = parseInteger(cast.nighttemp());
        String icon = iconCode(weather);
        return new TripWeatherDailyItem(
                date,
                weekdayText(date),
                weather,
                tempMin,
                tempMax,
                windText(cast.daywind(), cast.nightwind()),
                icon,
                null,
                tip(icon, tempMin, tempMax));
    }

    private String windText(String dayWind, String nightWind) {
        String day = cleanText(dayWind);
        if (StringUtils.hasText(day)) {
            return day;
        }
        return cleanText(nightWind);
    }

    private String weatherText(String dayWeather, String nightWeather) {
        String day = cleanText(dayWeather);
        String night = cleanText(nightWeather);
        if (!StringUtils.hasText(day) && !StringUtils.hasText(night)) {
            return "unknown";
        }
        if (!StringUtils.hasText(night) || day.equals(night)) {
            return day;
        }
        if (!StringUtils.hasText(day)) {
            return night;
        }
        return day + "/" + night;
    }

    private String iconCode(String weather) {
        String text = weather == null ? "" : weather;
        if (text.contains("晴")) {
            return "sunny";
        }
        if (text.contains("多云")) {
            return "cloudy";
        }
        if (text.contains("阴")) {
            return "overcast";
        }
        if (text.contains("大雨") || text.contains("暴雨")) {
            return "heavy_rain";
        }
        if (text.contains("中雨")) {
            return "moderate_rain";
        }
        if (text.contains("小雨") || text.contains("阵雨") || text.contains("雨")) {
            return "light_rain";
        }
        if (text.contains("雪")) {
            return "snow";
        }
        if (text.contains("雾") || text.contains("霾")) {
            return "fog";
        }
        return "unknown";
    }

    private String tip(String icon, Integer tempMin, Integer tempMax) {
        if (tempMax != null && tempMax >= 32) {
            return "气温较高，注意防晒和补水";
        }
        if (tempMin != null && tempMin <= 5) {
            return "气温偏低，记得准备保暖衣物";
        }
        return switch (icon) {
            case "sunny" -> "天气不错，适合安排户外行程";
            case "cloudy" -> "天气舒适，适合城市漫步";
            case "overcast" -> "天气较温和，适合轻松出行";
            case "light_rain", "moderate_rain" -> "可能有雨，记得带轻便雨伞";
            case "heavy_rain" -> "雨势较明显，建议预留室内行程";
            case "snow" -> "可能有雪，注意保暖和路况";
            case "fog" -> "能见度可能偏低，出行留足时间";
            default -> "天气信息已更新，可灵活安排行程";
        };
    }

    private Integer parseInteger(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Integer.valueOf(value.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private LocalDate parseDate(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private String weekdayText(LocalDate date) {
        return switch (date.getDayOfWeek()) {
            case MONDAY -> "周一";
            case TUESDAY -> "周二";
            case WEDNESDAY -> "周三";
            case THURSDAY -> "周四";
            case FRIDAY -> "周五";
            case SATURDAY -> "周六";
            case SUNDAY -> "周日";
        };
    }

    private String cleanText(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        return "[]".equals(trimmed) ? null : trimmed;
    }
}
