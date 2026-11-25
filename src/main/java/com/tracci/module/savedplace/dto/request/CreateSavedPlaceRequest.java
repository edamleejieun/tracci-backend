package com.tracci.module.savedplace.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

public record CreateSavedPlaceRequest(
        @Size(max = 64, message = "placeId 长度不能超过 64")
        String placeId,

        String placeScope,

        String category,

        @Valid
        LocationCandidate location,

        @Size(max = 500, message = "备注长度不能超过 500")
        String note
) {
}
