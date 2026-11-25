package com.tracci.module.placecontent.dto.request;

import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public record PlaceMediaUpdateRequest(
        @Size(max = 300, message = "altText 长度不能超过 300")
        String altText,

        String attribution,

        @Size(max = 80, message = "licenseType 长度不能超过 80")
        String licenseType,

        Integer sortOrder,

        LocalDateTime rightsExpiresAt
) {
}
