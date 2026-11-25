package com.tracci.module.savedplace.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PlaceBrief(
        String name,
        String placeScope,
        String category,
        String address,
        String cityName,
        String country,
        BigDecimal latitude,
        BigDecimal longitude,
        List<String> supportedUsages
) {
}
