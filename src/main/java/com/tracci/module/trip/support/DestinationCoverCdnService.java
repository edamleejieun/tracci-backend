package com.tracci.module.trip.support;

import com.tracci.integration.cos.CiUrlBuilder;
import com.tracci.integration.cos.CiUrlBuilder.CiUrls;
import com.tracci.integration.cos.CosClient;
import com.tracci.integration.cos.CosProperties;
import com.tracci.module.trip.config.TripProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.Optional;

/**
 * 国内城市系统封面：读取运营预置在 COS 上的图片，失败时回退内置默认图。
 * 不再从图库实时下载或镜像。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DestinationCoverCdnService {

    private static final String DEFAULT_CONTENT_TYPE = "image/jpeg";
    /** 预置封面未标注尺寸时的展示占位尺寸（与历史默认图一致）。 */
    private static final int PRESET_WIDTH = 1600;
    private static final int PRESET_HEIGHT = 1000;

    private final TripProperties tripProperties;
    private final CosProperties cosProperties;
    private final CosClient cosClient;
    private final CiUrlBuilder ciUrlBuilder;

    /**
     * 按 cityKey 组装预置封面 URL。
     * objectKey：{@code {citiesObjectKeyPrefix}/{cityKey}.jpg}
     */
    public Optional<CachedDestinationCover> presetCover(String cityKey) {
        if (!StringUtils.hasText(cityKey) || !cosProperties.isEnabled()) {
            return Optional.empty();
        }
        String objectKey = objectKeyForCity(cityKey.trim().toLowerCase(Locale.ROOT));
        try {
            return Optional.of(buildCoverFromObjectKey(
                    objectKey,
                    DEFAULT_CONTENT_TYPE,
                    PRESET_WIDTH,
                    PRESET_HEIGHT));
        } catch (Exception ex) {
            log.warn("Destination preset cover unavailable cityKey={} message={}", cityKey, ex.getMessage());
            return Optional.empty();
        }
    }

    public Optional<CachedDestinationCover> fallbackCover() {
        if (!cosProperties.isEnabled()) {
            return Optional.empty();
        }
        String fallbackKey = tripProperties.getDestinationCover().getFallbackObjectKey();
        if (!StringUtils.hasText(fallbackKey)) {
            return Optional.empty();
        }
        try {
            ensureFallbackObject(fallbackKey.trim());
            return Optional.of(buildCoverFromObjectKey(
                    fallbackKey.trim(),
                    DEFAULT_CONTENT_TYPE,
                    DefaultDestinationCoverAsset.WIDTH,
                    DefaultDestinationCoverAsset.HEIGHT));
        } catch (Exception ex) {
            log.warn("Destination cover fallback unavailable: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    /**
     * 缓存命中后刷新访问 URL。稳定 COS 域名直接返回；预签名 URL 每次重新签发。
     */
    public CachedDestinationCover materialize(CachedDestinationCover cached) {
        if (cached == null) {
            return null;
        }
        if (!StringUtils.hasText(cached.objectKey()) || usesStablePublicAccess()) {
            return cached;
        }
        return buildCoverFromObjectKey(
                cached.objectKey(),
                firstNonBlank(cached.contentType(), DEFAULT_CONTENT_TYPE),
                cached.width(),
                cached.height());
    }

    private void ensureFallbackObject(String objectKey) {
        try {
            byte[] existing = cosClient.getObjectBytes(objectKey);
            if (existing != null && existing.length > 0) {
                return;
            }
        } catch (Exception ignored) {
            // object missing — seed below
        }
        byte[] bytes = DefaultDestinationCoverAsset.jpegBytes();
        if (bytes == null || bytes.length == 0) {
            throw new IllegalStateException("bundled fallback cover unavailable");
        }
        cosClient.putObject(objectKey, bytes, DEFAULT_CONTENT_TYPE);
    }

    private CachedDestinationCover buildCoverFromObjectKey(
            String objectKey,
            String contentType,
            Integer width,
            Integer height) {
        if (usesStablePublicAccess()) {
            String baseUrl = resolveStablePublicBaseUrl() + "/" + objectKey;
            return buildCover(baseUrl, contentType, width, height, objectKey);
        }
        int expireSeconds = cosProperties.getAccessUrlExpireSeconds();
        String originalUrl = cosClient.generatePresignedAccessUrl(objectKey, expireSeconds);
        CiUrls ciUrls = ciUrlBuilder.buildSigned(
                originalUrl,
                contentType,
                rule -> cosClient.generatePresignedAccessUrl(objectKey, expireSeconds, rule));
        return fromCiUrls(ciUrls, width, height, objectKey, contentType);
    }

    private CachedDestinationCover buildCover(
            String baseUrl,
            String contentType,
            Integer width,
            Integer height,
            String objectKey) {
        CiUrls ciUrls = ciUrlBuilder.build(baseUrl, contentType);
        if (!StringUtils.hasText(ciUrls.url())) {
            return new CachedDestinationCover(
                    baseUrl,
                    baseUrl,
                    baseUrl,
                    baseUrl,
                    baseUrl,
                    baseUrl,
                    baseUrl,
                    width,
                    height,
                    objectKey,
                    contentType);
        }
        return fromCiUrls(ciUrls, width, height, objectKey, contentType);
    }

    private CachedDestinationCover fromCiUrls(
            CiUrls ciUrls,
            Integer width,
            Integer height,
            String objectKey,
            String contentType) {
        String preview = firstNonBlank(ciUrls.previewUrl(), ciUrls.url());
        String thumbnail = firstNonBlank(ciUrls.thumbnailUrl(), preview);
        String storyPortrait = firstNonBlank(ciUrls.storyPortraitUrl(), preview);
        String storyCard = firstNonBlank(ciUrls.storyCardUrl(), preview);
        return new CachedDestinationCover(
                ciUrls.url(),
                thumbnail,
                preview,
                firstNonBlank(ciUrls.exportUrl(), preview),
                storyPortrait,
                storyCard,
                firstNonBlank(ciUrls.squareUrl(), preview),
                width,
                height,
                objectKey,
                contentType);
    }

    private boolean usesStablePublicAccess() {
        return StringUtils.hasText(resolveStablePublicBaseUrl());
    }

    /**
     * 只有显式配置的 public-base-url 才能证明对象可公开访问。
     * bucket/region 只能确定 COS 地址，不能证明私有桶允许匿名读取。
     */
    private String resolveStablePublicBaseUrl() {
        String configured = cosProperties.getPublicBaseUrl();
        if (StringUtils.hasText(configured)) {
            return trimTrailingSlash(configured.trim());
        }
        return null;
    }

    String objectKeyForCity(String cityKey) {
        String prefix = tripProperties.getDestinationCover().getCitiesObjectKeyPrefix();
        if (!StringUtils.hasText(prefix)) {
            prefix = "system/destination-covers/v1/cities";
        }
        prefix = prefix.trim();
        if (prefix.endsWith("/")) {
            prefix = prefix.substring(0, prefix.length() - 1);
        }
        return prefix + "/" + cityKey.trim().toLowerCase(Locale.ROOT) + ".jpg";
    }

    private String trimTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }
}
