package com.tracci.module.file.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AccessUrlResp(
        String fileId,
        String url,
        String thumbnailUrl,
        String previewUrl,
        String exportUrl,
        String storyPortraitUrl,
        String storyCardUrl,
        String squareUrl,
        String watermarkedUrl,
        String expiresAt
) {
}
