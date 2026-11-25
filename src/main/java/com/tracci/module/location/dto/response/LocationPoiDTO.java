package com.tracci.module.location.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record LocationPoiDTO(
        String poiId,
        String name,
        String address,
        BigDecimal latitude,
        BigDecimal longitude,
        String provinceName,
        String cityName,
        String districtName,
        String adcode,
        String cityCode,
        String typeCode,
        String typeName,
        String source
) {
}
