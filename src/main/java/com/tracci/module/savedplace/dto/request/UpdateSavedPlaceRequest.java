package com.tracci.module.savedplace.dto.request;

import jakarta.validation.constraints.Size;

public record UpdateSavedPlaceRequest(
        @Size(max = 500, message = "备注长度不能超过 500")
        String note
) {
}
