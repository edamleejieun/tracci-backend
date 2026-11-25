package com.tracci.module.placecontent.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PlaceMediaAdminItem(
        String id,
        String placeId,
        String fileId,
        String status,
        Integer sortOrder,
        String altText,
        String attribution,
        String licenseType,
        Integer width,
        Integer height,
        LocalDateTime rightsExpiresAt,
        LocalDateTime reviewedAt,
        String url,
        String thumbnailUrl,
        String previewUrl
) {
}
