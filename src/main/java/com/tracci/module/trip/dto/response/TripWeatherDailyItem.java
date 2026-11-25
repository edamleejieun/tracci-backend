package com.tracci.module.trip.dto.response;

import java.time.LocalDate;

public record TripWeatherDailyItem(
        LocalDate date,
        String weekday,
        String weather,
        Integer tempMin,
        Integer tempMax,
        String wind,
        String icon,
        Integer pop,
        String tip
) {
}
