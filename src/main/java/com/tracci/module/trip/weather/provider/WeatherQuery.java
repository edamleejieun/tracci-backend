package com.tracci.module.trip.weather.provider;

import java.math.BigDecimal;

public record WeatherQuery(
        String tripId,
        BigDecimal latitude,
        BigDecimal longitude,
        String city,
        String displayCity,
        String country,
        Integer days,
        String clientPlatform,
        java.time.LocalDate queryStart,
        java.time.LocalDate queryEnd
) {
}
