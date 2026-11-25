package com.tracci.module.location.provider;

import java.math.BigDecimal;
import java.util.Map;

public record PlaceSearchResult(
        String name,
        String address,
        BigDecimal latitude,
        BigDecimal longitude,
        String city,
        String country,
        String provider,
        String providerPlaceId,
        Map<String, Object> providerMetadata
) {
}
