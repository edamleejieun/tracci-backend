package com.tracci.module.place.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.tracci.module.placecontent.dto.response.ContentMetaDto;
import com.tracci.module.placecontent.dto.response.EditorialContentDto;
import com.tracci.module.placecontent.dto.response.MediaAssetDto;

import java.math.BigDecimal;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PlaceDetailResponse(
        String id,
        String name,
        String placeScope,
        String category,
        String address,
        String cityName,
        String country,
        BigDecimal latitude,
        BigDecimal longitude,
        List<String> supportedUsages,
        PlaceUserContext userContext,
        EditorialContentDto editorialContent,
        ContentMetaDto contentMeta,
        List<MediaAssetDto> media
) {
}
