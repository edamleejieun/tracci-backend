package com.tracci.module.trip.support;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tracci.module.summary.dto.response.SummaryResp;
import com.tracci.module.trip.config.TripProperties;
import com.tracci.module.trip.dto.response.TripCoverPhotoResp;
import com.tracci.module.trip.entity.Trip;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.Optional;

/**
 * 无用户上传封面时，按国内目的地 cityKey 返回 COS 预置封面。
 * {@code fileId=null} 表示系统推荐图，非用户文件。
 * 不再调用 Pexels 等境外图库。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TripDestinationCoverProvider {

    /** v2：预置 COS 策略，与旧 Pexels 镜像缓存隔离。 */
    private static final String CACHE_PREFIX = "trip:dest-cover:preset:v2:";
    private static final String FALLBACK_CACHE_KEY = CACHE_PREFIX + "__fallback__";

    private final TripProperties tripProperties;
    private final DestinationCoverCityResolver cityResolver;
    private final DestinationCoverCdnService destinationCoverCdnService;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public Optional<TripCoverPhotoResp> resolveTripCover(Trip trip) {
        if (trip == null || !tripProperties.getDestinationCover().isEnabled()) {
            return Optional.empty();
        }
        Optional<String> cityKey = cityResolver.resolveCityKey(trip);
        if (cityKey.isPresent()) {
            return resolvePreset(cityKey.get())
                    .or(this::resolveFallback)
                    .map(this::toTripCover);
        }
        // 有目的地文案但未进国内预置表 → 默认图；完全无目的地 → 不返回系统封面
        if (!hasDestinationLabel(trip)) {
            return Optional.empty();
        }
        return resolveFallback().map(this::toTripCover);
    }

    private boolean hasDestinationLabel(Trip trip) {
        return StringUtils.hasText(trip.getDestinationCityName())
                || StringUtils.hasText(trip.getDestinationName());
    }

    public Optional<SummaryResp.CoverPhoto> resolveSummaryCover(Trip trip) {
        return resolveTripCover(trip).map(this::toSummaryCover);
    }

    private Optional<CachedDestinationCover> resolvePreset(String cityKey) {
        String cacheKey = CACHE_PREFIX + cityKey;
        Optional<CachedDestinationCover> cached = readCached(cacheKey);
        if (cached.isPresent()) {
            return cached;
        }

        Optional<CachedDestinationCover> resolved = destinationCoverCdnService.presetCover(cityKey);
        resolved.ifPresent(cover -> writeCache(cacheKey, cover));
        if (resolved.isEmpty()) {
            log.debug("No preset destination cover for cityKey={}", cityKey);
        }
        return resolved;
    }

    private Optional<CachedDestinationCover> resolveFallback() {
        Optional<CachedDestinationCover> cached = readCached(FALLBACK_CACHE_KEY);
        if (cached.isPresent()) {
            return cached;
        }
        Optional<CachedDestinationCover> resolved = destinationCoverCdnService.fallbackCover();
        resolved.ifPresent(cover -> writeCache(FALLBACK_CACHE_KEY, cover));
        return resolved;
    }

    private Optional<CachedDestinationCover> readCached(String cacheKey) {
        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (!StringUtils.hasText(cached)) {
            return Optional.empty();
        }
        return readCache(cached).map(destinationCoverCdnService::materialize);
    }

    private TripCoverPhotoResp toTripCover(CachedDestinationCover cover) {
        return new TripCoverPhotoResp(
                null,
                cover.url(),
                cover.thumbnailUrl(),
                cover.previewUrl(),
                cover.exportUrl(),
                cover.storyPortraitUrl(),
                cover.storyCardUrl(),
                cover.squareUrl(),
                null,
                cover.width(),
                cover.height());
    }

    private void writeCache(String cacheKey, CachedDestinationCover cover) {
        try {
            redisTemplate.opsForValue().set(
                    cacheKey,
                    objectMapper.writeValueAsString(cover),
                    Duration.ofMinutes(Math.max(tripProperties.getDestinationCover().getCacheTtlMinutes(), 1)));
        } catch (JsonProcessingException ex) {
            log.debug("Failed to cache destination cover: {}", ex.getMessage());
        }
    }

    private Optional<CachedDestinationCover> readCache(String cached) {
        try {
            return Optional.of(objectMapper.readValue(cached, CachedDestinationCover.class));
        } catch (JsonProcessingException ex) {
            return Optional.empty();
        }
    }

    private SummaryResp.CoverPhoto toSummaryCover(TripCoverPhotoResp cover) {
        return new SummaryResp.CoverPhoto(
                cover.fileId(),
                cover.url(),
                cover.thumbnailUrl(),
                cover.previewUrl(),
                cover.exportUrl(),
                cover.storyPortraitUrl(),
                cover.storyCardUrl(),
                cover.squareUrl(),
                cover.watermarkedUrl(),
                cover.width(),
                cover.height());
    }
}
