package com.tracci.module.trip.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ItineraryNodeResp(
        String id,
        String tripDayId,
        String title,
        String type,
        String startTime,
        String endTime,
        String address,
        ItineraryNodeLocationResp location,
        String note,
        Map<String, Object> details,
        Integer sortOrder,
        String createdAt
) {
}
