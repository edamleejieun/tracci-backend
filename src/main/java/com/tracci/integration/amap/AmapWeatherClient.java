package com.tracci.integration.amap;

import com.tracci.integration.amap.dto.AmapWeatherResponse;

public interface AmapWeatherClient {

    AmapWeatherResponse forecast(String city);
}
