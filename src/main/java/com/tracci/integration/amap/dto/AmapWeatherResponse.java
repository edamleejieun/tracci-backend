package com.tracci.integration.amap.dto;

import java.util.List;

public record AmapWeatherResponse(
        String status,
        String count,
        String info,
        String infocode,
        List<AmapForecastDTO> forecasts
) {
}
