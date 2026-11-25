package com.tracci.module.trip.support;

/**
 * 目的地系统封面 COS 资产（城市预置图或默认图）。
 * {@code objectKey} 用于缓存命中后按需刷新访问 URL（预签名场景）。
 */
public record CachedDestinationCover(
        String url,
        String thumbnailUrl,
        String previewUrl,
        String exportUrl,
        String storyPortraitUrl,
        String storyCardUrl,
        String squareUrl,
        Integer width,
        Integer height,
        String objectKey,
        String contentType
) {
}
