package com.tracci.module.location.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record LocationPoiSearchResp(
        List<LocationPoiDTO> items,
        Integer page,
        Integer size,
        Boolean hasMore,
        String source,
        String status
) {
    public LocationPoiSearchResp(
            List<LocationPoiDTO> items,
            Integer page,
            Integer size,
            Boolean hasMore,
            String source
    ) {
        this(items, page, size, hasMore, source, null);
    }
}
