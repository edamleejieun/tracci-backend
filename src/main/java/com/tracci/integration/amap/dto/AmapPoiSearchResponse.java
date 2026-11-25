package com.tracci.integration.amap.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AmapPoiSearchResponse(
        String status,
        String info,
        String infocode,
        String count,
        List<AmapPoiDTO> pois
) {
}
