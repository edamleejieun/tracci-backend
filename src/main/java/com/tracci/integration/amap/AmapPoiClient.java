package com.tracci.integration.amap;

import com.tracci.integration.amap.dto.AmapPoiSearchResponse;
import com.tracci.integration.amap.dto.AmapPoiDTO;

public interface AmapPoiClient {

    AmapPoiSearchResponse searchText(String keyword, String city, int page, int size, String types);

    /**
     * 按高德 POI ID 获取权威地点快照；找不到时返回 null。
     */
    AmapPoiDTO getDetail(String providerPlaceId);
}
