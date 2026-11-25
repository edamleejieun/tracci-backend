package com.tracci.module.stats.dto.response;

import java.util.List;

public record StatsFootprintAnalysisResp(
        long destinationCount,
        long itineraryCount,
        long totalTravelDays,
        String insight,
        List<CityFootprint> cities,
        List<NodeTypeStat> nodeTypes
) {

    public record CityFootprint(
            String name,
            String adcode,
            String cityCode,
            String provinceName,
            String latitude,
            String longitude,
            long tripCount,
            long totalDays,
            long itineraryCount,
            String firstTripStartDate
    ) {
    }

    public record NodeTypeStat(
            String type,
            String typeName,
            long count
    ) {
    }
}
