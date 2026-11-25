package com.tracci.module.place.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PlaceUserContext(
        boolean isSaved,
        String savedPlaceId,
        String note,
        PlaceUsageSummary usageSummary
) {
}
