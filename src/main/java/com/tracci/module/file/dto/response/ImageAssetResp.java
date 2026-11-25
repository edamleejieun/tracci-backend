package com.tracci.module.file.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.tracci.integration.cos.CiUrlBuilder;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ImageAssetResp(
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

    public static ImageAssetResp of(String fileId, CiUrlBuilder.CiUrls ciUrls, Integer width, Integer height) {
        return new ImageAssetResp(
                fileId,
                ciUrls.url(),
                ciUrls.thumbnailUrl(),
                ciUrls.previewUrl(),
                ciUrls.exportUrl(),
                ciUrls.storyPortraitUrl(),
                ciUrls.storyCardUrl(),
                ciUrls.squareUrl(),
                ciUrls.watermarkedUrl(),
                width,
                height);
    }
}
