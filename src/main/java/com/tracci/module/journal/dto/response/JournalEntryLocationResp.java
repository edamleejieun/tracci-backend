package com.tracci.module.journal.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.tracci.module.trip.dto.response.ItineraryNodeLocationResp;

import java.math.BigDecimal;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record JournalEntryLocationResp(
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
        String country) {

    public static JournalEntryLocationResp from(ItineraryNodeLocationResp location) {
        return new JournalEntryLocationResp(
                location.poiId(),
                location.name(),
                location.address(),
                location.latitude(),
                location.longitude(),
                location.adcode(),
                location.cityCode(),
                location.provinceName(),
                location.cityName(),
                location.districtName(),
                location.provider(),
                location.providerPlaceId(),
                location.country());
    }
}
