package com.tracci.module.place.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PlaceListItemResponse(
        String id,
        String name,
        String placeScope,
        String category,
        String cityName,
        String country,
        BigDecimal latitude,
        BigDecimal longitude,
        List<String> supportedUsages,
        String highlight,
        LocalDateTime publishedAt,
        boolean isSaved,
        PlaceCoverResponse cover
) {
    public PlaceListItemResponse withIsSaved(boolean saved) {
        return new PlaceListItemResponse(
                id, name, placeScope, category, cityName, country,
                latitude, longitude, supportedUsages, highlight, publishedAt, saved, cover);
    }
}
