package com.tracci.module.trip.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TripDestinationResp(
        String name,
        String adcode,
        String cityCode,
        String provinceName,
        String cityName,
        String districtName,
        BigDecimal latitude,
        BigDecimal longitude,
        String provider,
        String providerPlaceId,
        String country,
        String placeId
) {
}
