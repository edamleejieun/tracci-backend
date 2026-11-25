package com.tracci.module.location.provider;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tracci.integration.amap.AmapPoiClient;
import com.tracci.integration.amap.AmapProperties;
import com.tracci.integration.amap.dto.AmapPoiDTO;
import com.tracci.integration.amap.dto.AmapPoiSearchResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class AmapPlaceSearchProvider implements PlaceSearchProvider {

    private static final String PROVIDER = PlaceSearchProviderType.AMAP.name();
    private static final String CACHE_PREFIX = "location:poi:amap:";

    private final AmapPoiClient amapPoiClient;
    private final AmapProperties amapProperties;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    @Override
    public PlaceSearchProviderType providerType() {
        return PlaceSearchProviderType.AMAP;
    }

    @Override
    public PlaceSearchPage search(PlaceSearchQuery query) {
        int page = query.page() == null ? 1 : query.page();
        int size = query.limit() == null ? amapProperties.getPoi().getDefaultPageSize() : query.limit();
        size = Math.min(Math.max(size, 1), amapProperties.getPoi().getMaxPageSize());

        String cacheKey = cacheKey(query.keyword(), query.city(), query.latitude(), query.longitude(), page, size);
        PlaceSearchPage cached = readCache(cacheKey);
        if (cached != null) {
            return cached;
        }

        AmapPoiSearchResponse amapResp = amapPoiClient.searchText(query.keyword(), query.city(), page, size, null);
        List<PlaceSearchResult> items = amapResp.pois() == null
                ? List.of()
                : amapResp.pois().stream()
                .map(this::toPlaceSearchResult)
                .filter(result -> StringUtils.hasText(result.name()))
                .toList();
        int count = parseCount(amapResp.count());
        PlaceSearchPage pageResult = new PlaceSearchPage(items, count);
        writeCache(cacheKey, pageResult, items.isEmpty());
        return pageResult;
    }

    private PlaceSearchResult toPlaceSearchResult(AmapPoiDTO poi) {
        Coordinate coordinate = parseCoordinate(poi.location());
        Map<String, Object> metadata = new LinkedHashMap<>();
        putIfPresent(metadata, "adcode", cleanText(poi.adcode()));
        putIfPresent(metadata, "cityCode", cleanText(poi.citycode()));
        putIfPresent(metadata, "typeCode", cleanText(poi.typecode()));
        putIfPresent(metadata, "typeName", cleanText(poi.type()));
        putIfPresent(metadata, "provinceName", firstText(poi.pname()));
        putIfPresent(metadata, "districtName", firstText(poi.adname()));

        return new PlaceSearchResult(
                cleanText(poi.name()),
                firstText(poi.address()),
                coordinate.latitude(),
                coordinate.longitude(),
                firstText(poi.cityname()),
                null,
                PROVIDER,
                cleanText(poi.id()),
                metadata.isEmpty() ? null : metadata
        );
    }

    private void putIfPresent(Map<String, Object> metadata, String key, String value) {
        if (StringUtils.hasText(value)) {
            metadata.put(key, value);
        }
    }

    private String cleanText(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        return "[]".equals(trimmed) ? null : trimmed;
    }

    private Coordinate parseCoordinate(String location) {
        if (!StringUtils.hasText(location)) {
            return new Coordinate(null, null);
        }
        String[] parts = location.split(",");
        if (parts.length != 2) {
            return new Coordinate(null, null);
        }
        try {
            BigDecimal longitude = new BigDecimal(parts[0].trim());
            BigDecimal latitude = new BigDecimal(parts[1].trim());
            return new Coordinate(latitude, longitude);
        } catch (NumberFormatException ex) {
            return new Coordinate(null, null);
        }
    }

    private String firstText(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        if (node.isArray()) {
            for (JsonNode item : node) {
                String text = firstText(item);
                if (StringUtils.hasText(text)) {
                    return text;
                }
            }
            return null;
        }
        if (node.isObject()) {
            return null;
        }
        String text = node.asText(null);
        if (!StringUtils.hasText(text) || "[]".equals(text)) {
            return null;
        }
        return text.trim();
    }

    private int parseCount(String count) {
        if (!StringUtils.hasText(count)) {
            return 0;
        }
        try {
            return Math.max(Integer.parseInt(count), 0);
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private String cacheKey(String keyword, String city, BigDecimal latitude, BigDecimal longitude, int page, int size) {
        return CACHE_PREFIX
                + keyword + ":"
                + (city == null ? "" : city) + ":"
                + (latitude == null ? "" : latitude) + ":"
                + (longitude == null ? "" : longitude) + ":"
                + page + ":"
                + size;
    }

    private PlaceSearchPage readCache(String cacheKey) {
        try {
            String raw = redis.opsForValue().get(cacheKey);
            if (!StringUtils.hasText(raw)) {
                return null;
            }
            return objectMapper.readValue(raw, PlaceSearchPage.class);
        } catch (Exception ex) {
            log.debug("Read POI cache failed key={}: {}", cacheKey, ex.getMessage());
            return null;
        }
    }

    private void writeCache(String cacheKey, PlaceSearchPage response, boolean emptyResult) {
        try {
            int minutes = Math.max(amapProperties.getPoi().getCacheTtlMinutes(), 1);
            Duration ttl = Duration.ofMinutes(emptyResult ? Math.min(minutes, 30) : minutes);
            redis.opsForValue().set(cacheKey, objectMapper.writeValueAsString(response), ttl);
        } catch (JsonProcessingException ex) {
            log.debug("Serialize POI cache failed key={}: {}", cacheKey, ex.getMessage());
        } catch (RuntimeException ex) {
            log.debug("Write POI cache failed key={}: {}", cacheKey, ex.getMessage());
        }
    }

    private record Coordinate(BigDecimal latitude, BigDecimal longitude) {
    }
}
