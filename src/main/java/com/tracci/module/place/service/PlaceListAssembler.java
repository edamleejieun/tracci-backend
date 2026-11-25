package com.tracci.module.place.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tracci.module.place.dto.response.PlaceCoverResponse;
import com.tracci.module.place.dto.response.PlaceListItemResponse;
import com.tracci.module.place.enums.PlaceScope;
import com.tracci.module.place.repository.PublishedPlaceRow;
import com.tracci.module.placecontent.dto.response.MediaAssetDto;
import com.tracci.module.savedplace.enums.UsageType;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 探索列表卡片字段派生：highlight / cover / supportedUsages。
 */
final class PlaceListAssembler {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final int INTRO_HIGHLIGHT_MAX_CODE_POINTS = 40;
    private static final String ELLIPSIS = "…";

    private PlaceListAssembler() {
    }

    static PlaceListItemResponse toItem(PublishedPlaceRow row, MediaAssetDto coverMedia) {
        return new PlaceListItemResponse(
                row.getId(),
                row.getCanonicalName(),
                row.getPlaceScope(),
                row.getCategory(),
                row.getCityName(),
                row.getCountry(),
                row.getLatitude(),
                row.getLongitude(),
                supportedUsages(row.getPlaceScope()),
                deriveHighlight(row.getHighlightsJson(), row.getIntroduction()),
                row.getPublishedAt(),
                false,
                toCover(coverMedia)
        );
    }

    static List<String> supportedUsages(String placeScope) {
        List<String> usages = new ArrayList<>(1);
        if (PlaceScope.DESTINATION.name().equals(placeScope)) {
            usages.add(UsageType.TRIP_DESTINATION.name());
        } else if (PlaceScope.POI.name().equals(placeScope)) {
            usages.add(UsageType.ITINERARY_NODE.name());
        }
        return usages;
    }

    static String deriveHighlight(String highlightsJson, String introduction) {
        for (String item : parseJsonStringList(highlightsJson)) {
            String trimmed = normalizeOptional(item);
            if (trimmed != null) {
                return trimmed;
            }
        }
        String intro = normalizeOptional(introduction);
        if (intro == null) {
            return null;
        }
        int count = intro.codePointCount(0, intro.length());
        if (count <= INTRO_HIGHLIGHT_MAX_CODE_POINTS) {
            return intro;
        }
        int end = intro.offsetByCodePoints(0, INTRO_HIGHLIGHT_MAX_CODE_POINTS);
        return intro.substring(0, end) + ELLIPSIS;
    }

    static PlaceCoverResponse toCover(MediaAssetDto media) {
        if (media == null) {
            return null;
        }
        String url = firstText(media.thumbnailUrl(), media.previewUrl(), media.url());
        if (url == null) {
            return null;
        }
        return new PlaceCoverResponse(media.id(), url, media.altText(), media.attribution());
    }

    private static List<String> parseJsonStringList(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            List<String> parsed = OBJECT_MAPPER.readValue(json, new TypeReference<>() {
            });
            return parsed == null ? List.of() : parsed;
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }

    private static String firstText(String... values) {
        for (String value : values) {
            String normalized = normalizeOptional(value);
            if (normalized != null) {
                return normalized;
            }
        }
        return null;
    }

    private static String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return StringUtils.hasText(trimmed) ? trimmed : null;
    }
}
