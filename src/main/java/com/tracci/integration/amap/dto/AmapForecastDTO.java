package com.tracci.integration.amap.dto;

import java.util.List;

public record AmapForecastDTO(
        String city,
        String adcode,
        String province,
        String reporttime,
        List<AmapCastDTO> casts
) {
}
