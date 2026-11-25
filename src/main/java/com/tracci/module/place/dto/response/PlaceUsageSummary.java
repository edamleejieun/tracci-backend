package com.tracci.module.place.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PlaceUsageSummary(
        long tripCount,
        String lastUsedTripId
) {
}
