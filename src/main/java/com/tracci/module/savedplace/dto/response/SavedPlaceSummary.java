package com.tracci.module.savedplace.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.tracci.module.place.dto.response.PlaceUsageSummary;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record SavedPlaceSummary(
        String id,
        String placeId,
        PlaceBrief place,
        String note,
        PlaceUsageSummary usageSummary,
        String createdAt,
        String updatedAt
) {
}
