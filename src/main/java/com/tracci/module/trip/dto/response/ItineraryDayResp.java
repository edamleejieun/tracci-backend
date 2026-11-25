package com.tracci.module.trip.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ItineraryDayResp(
        String id,
        Integer dayIndex,
        String date,
        List<ItineraryNodeBriefResp> nodes
) {
}
