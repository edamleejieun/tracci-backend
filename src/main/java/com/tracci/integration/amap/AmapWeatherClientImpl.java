package com.tracci.integration.amap;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tracci.common.exception.BizException;
import com.tracci.common.exception.ThirdPartyException;
import com.tracci.common.response.ErrorCode;
import com.tracci.integration.amap.dto.AmapWeatherResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.net.URI;

@Slf4j
class AmapWeatherClientImpl implements AmapWeatherClient {

    private final AmapProperties properties;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    AmapWeatherClientImpl(AmapProperties properties, RestClient.Builder restClientBuilder, ObjectMapper objectMapper) {
        this.properties = properties;
        this.restClient = restClientBuilder.build();
        this.objectMapper = objectMapper;
    }

    @Override
    public AmapWeatherResponse forecast(String city) {
        if (!properties.isEnabled()
                || !properties.getWeather().isEnabled()
                || !StringUtils.hasText(properties.getWebServiceKey())) {
            throw new BizException(ErrorCode.LOCATION_SEARCH_UNAVAILABLE, "天气服务未配置");
        }
        try {
            URI uri = URI.create(properties.getWeather().getEndpoint());
            String raw = restClient.get()
                    .uri(builder -> builder
                            .scheme(uri.getScheme())
                            .host(uri.getHost())
                            .path(uri.getPath())
                            .queryParam("key", properties.getWebServiceKey())
                            .queryParam("city", city)
                            .queryParam("extensions", "all")
                            .queryParam("output", "JSON")
                            .build())
                    .retrieve()
                    .body(String.class);
            AmapWeatherResponse response = objectMapper.readValue(raw, AmapWeatherResponse.class);
            validateAmapResponse(response);
            return response;
        } catch (BizException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("Amap weather forecast failed cityPresent={} exception={}",
                    StringUtils.hasText(city), ex.getClass().getSimpleName());
            throw new ThirdPartyException(ErrorCode.LOCATION_SEARCH_UNAVAILABLE);
        }
    }

    private void validateAmapResponse(AmapWeatherResponse response) {
        if (response == null) {
            throw new ThirdPartyException(ErrorCode.LOCATION_SEARCH_UNAVAILABLE);
        }
        if (!"1".equals(response.status())) {
            log.warn("Amap weather returned error infocode={} info={}", response.infocode(), response.info());
            throw new ThirdPartyException(ErrorCode.LOCATION_SEARCH_UNAVAILABLE);
        }
    }
}
