package com.tracci.module.trip.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TripListResp(
        String id,
        String title,
        String startDate,
        String endDate,
        String status,
        long totalDays,
        TripDestinationResp destination,
        TripCoverPhotoResp coverPhoto,
        String totalCny,
        long nodeCount,
        long journalCount
) {
}
