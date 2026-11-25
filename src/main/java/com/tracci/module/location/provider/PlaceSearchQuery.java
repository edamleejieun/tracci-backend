package com.tracci.module.location.provider;

import java.math.BigDecimal;

public record PlaceSearchQuery(
        String keyword,
        String city,
        BigDecimal latitude,
        BigDecimal longitude,
        Integer page,
        Integer limit,
        String clientPlatform
) {
}
