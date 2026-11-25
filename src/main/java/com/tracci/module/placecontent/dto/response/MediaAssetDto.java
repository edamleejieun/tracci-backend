package com.tracci.module.placecontent.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 媒体资产展示。嵌入 PlaceDetailResponse，仅返回 APPROVED 且未过期的资产。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MediaAssetDto(
        String id,
        String url,
        String thumbnailUrl,
        String previewUrl,
        Integer width,
        Integer height,
        String altText,
        String attribution
) {
}
