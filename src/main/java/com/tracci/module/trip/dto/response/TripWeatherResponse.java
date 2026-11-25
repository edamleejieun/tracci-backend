package com.tracci.module.trip.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDate;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TripWeatherResponse(
        Boolean available,
        String status,
        String source,
        String forecastType,
        String message,
        LocalDate updatedAt,
        String locationName,
        List<TripWeatherDailyItem> items,
        Integer forecastLimitDays,
        TripWeatherLocationResp location
) {
    public TripWeatherResponse(
            Boolean available,
            String status,
            String source,
            String forecastType,
            String message,
            LocalDate updatedAt,
            String locationName,
            List<TripWeatherDailyItem> items,
            Integer forecastLimitDays
    ) {
        this(available, status, source, forecastType, message, updatedAt, locationName, items, forecastLimitDays, null);
    }
}
