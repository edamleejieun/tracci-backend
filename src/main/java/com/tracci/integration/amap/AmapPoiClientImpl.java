package com.tracci.integration.amap;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tracci.common.exception.BizException;
import com.tracci.common.exception.ThirdPartyException;
import com.tracci.common.response.ErrorCode;
import com.tracci.integration.amap.dto.AmapPoiSearchResponse;
import com.tracci.integration.amap.dto.AmapPoiDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.net.URI;

@Slf4j
class AmapPoiClientImpl implements AmapPoiClient {

    private static final String SOURCE = "AMAP";

    private final AmapProperties properties;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    AmapPoiClientImpl(AmapProperties properties, RestClient.Builder restClientBuilder, ObjectMapper objectMapper) {
        this.properties = properties;
        this.restClient = restClientBuilder.build();
        this.objectMapper = objectMapper;
    }

    @Override
    public AmapPoiSearchResponse searchText(String keyword, String city, int page, int size, String types) {
        if (!properties.isEnabled() || !StringUtils.hasText(properties.getWebServiceKey())) {
            throw new BizException(ErrorCode.LOCATION_SEARCH_UNAVAILABLE, "地点搜索服务未配置");
        }
        try {
            URI uri = URI.create(properties.getPoi().getEndpoint());
            String raw = restClient.get()
                    .uri(builder -> {
                        var uriBuilder = builder
                                .scheme(uri.getScheme())
                                .host(uri.getHost())
                                .path(uri.getPath())
                                .queryParam("key", properties.getWebServiceKey())
                                .queryParam("keywords", keyword)
                                .queryParam("page_num", page)
                                .queryParam("page_size", size)
                                .queryParam("show_fields", "children,business,indoor,navi,photos");
                        if (StringUtils.hasText(city)) {
                            uriBuilder.queryParam("region", city).queryParam("city_limit", true);
                        }
                        if (StringUtils.hasText(types)) {
                            uriBuilder.queryParam("types", types);
                        }
                        return uriBuilder.build();
                    })
                    .retrieve()
                    .body(String.class);
            AmapPoiSearchResponse response = objectMapper.readValue(raw, AmapPoiSearchResponse.class);
            validateAmapResponse(response);
            return response;
        } catch (BizException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("Amap POI search failed source={} keywordLength={} cityPresent={} message={}",
                    SOURCE, keyword.length(), StringUtils.hasText(city), ex.getMessage());
            throw new ThirdPartyException(ErrorCode.LOCATION_SEARCH_UNAVAILABLE);
        }
    }

    @Override
    public AmapPoiDTO getDetail(String providerPlaceId) {
        if (!properties.isEnabled() || !StringUtils.hasText(properties.getWebServiceKey())) {
            throw new BizException(ErrorCode.LOCATION_SEARCH_UNAVAILABLE, "地点搜索服务未配置");
        }
        if (!StringUtils.hasText(providerPlaceId)) {
            throw new BizException(ErrorCode.PLACE_IDENTITY_UNAVAILABLE, "高德地点 ID 不能为空");
        }
        try {
            URI uri = URI.create(properties.getPoi().getDetailEndpoint());
            String raw = restClient.get()
                    .uri(builder -> builder
                            .scheme(uri.getScheme())
                            .host(uri.getHost())
                            .path(uri.getPath())
                            .queryParam("key", properties.getWebServiceKey())
                            .queryParam("id", providerPlaceId.trim())
                            .queryParam("show_fields", "business")
                            .build())
                    .retrieve()
                    .body(String.class);
            AmapPoiSearchResponse response = objectMapper.readValue(raw, AmapPoiSearchResponse.class);
            validateAmapResponse(response);
            if (response.pois() == null || response.pois().isEmpty()) {
                return null;
            }
            return response.pois().getFirst();
        } catch (BizException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("Amap POI detail failed source={} providerPlaceIdLength={} message={}",
                    SOURCE, providerPlaceId.length(), ex.getMessage());
            throw new ThirdPartyException(ErrorCode.LOCATION_SEARCH_UNAVAILABLE);
        }
    }

    private void validateAmapResponse(AmapPoiSearchResponse response) {
        if (response == null) {
            throw new ThirdPartyException(ErrorCode.LOCATION_SEARCH_UNAVAILABLE);
        }
        if (!"1".equals(response.status())) {
            log.warn("Amap POI returned error infocode={} info={}", response.infocode(), response.info());
            throw new ThirdPartyException(ErrorCode.LOCATION_SEARCH_UNAVAILABLE);
        }
    }
}
