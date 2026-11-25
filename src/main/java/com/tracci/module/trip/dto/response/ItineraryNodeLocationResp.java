package com.tracci.module.trip.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ItineraryNodeLocationResp(
        String poiId,
        String name,
        String address,
        BigDecimal latitude,
        BigDecimal longitude,
        String adcode,
        String cityCode,
        String provinceName,
        String cityName,
        String districtName,
        String provider,
        String providerPlaceId,
        String country,
        String placeId
) {
}
