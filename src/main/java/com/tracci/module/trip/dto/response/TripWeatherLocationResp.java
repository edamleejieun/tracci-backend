package com.tracci.module.trip.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TripWeatherLocationResp(
        String city,
        BigDecimal latitude,
        BigDecimal longitude,
        String country
) {
}
