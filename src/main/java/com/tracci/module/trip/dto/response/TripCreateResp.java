package com.tracci.module.trip.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TripCreateResp(
        String id,
        String title,
        String startDate,
        String endDate,
        String status,
        long totalDays,
        TripDestinationResp destination,
        TripCoverPhotoResp coverPhoto,
        String baseCurrency,
        String createdAt
) {
}
