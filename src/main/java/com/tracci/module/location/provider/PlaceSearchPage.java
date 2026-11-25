package com.tracci.module.location.provider;

import java.util.List;

public record PlaceSearchPage(
        List<PlaceSearchResult> items,
        int totalCount
) {
}
