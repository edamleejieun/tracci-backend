package com.tracci.integration.amap.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AmapPoiDTO(
        String id,
        String name,
        JsonNode address,
        String location,
        JsonNode pname,
        JsonNode cityname,
        JsonNode adname,
        String adcode,
        String citycode,
        String typecode,
        String type
) {
}
