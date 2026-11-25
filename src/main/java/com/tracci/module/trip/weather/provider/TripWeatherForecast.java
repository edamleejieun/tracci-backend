package com.tracci.module.trip.weather.provider;

import com.tracci.module.trip.dto.response.TripWeatherDailyItem;
import com.tracci.module.trip.dto.response.TripWeatherLocationResp;
import com.tracci.module.trip.enums.TripWeatherStatus;
import com.tracci.module.trip.enums.WeatherProvider;

import java.util.List;

public record TripWeatherForecast(
        TripWeatherStatus status,
        WeatherProvider provider,
        String message,
        List<TripWeatherDailyItem> daily,
        TripWeatherLocationResp location
) {
}
