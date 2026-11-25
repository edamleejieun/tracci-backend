package com.tracci.module.trip.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TripCoverPhotoResp(
        String fileId,
        String url,
        String thumbnailUrl,
        String previewUrl,
        String exportUrl,
        String storyPortraitUrl,
        String storyCardUrl,
        String squareUrl,
        String watermarkedUrl,
        Integer width,
        Integer height
) {
}
