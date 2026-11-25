package com.tracci.module.placecontent.service;

import com.tracci.module.location.dto.response.LocationPoiDTO;
import com.tracci.module.placecontent.dto.request.PlaceAdminCreateRequest;
import com.tracci.module.placecontent.dto.request.PlaceAdminUpdateRequest;
import com.tracci.module.placecontent.dto.request.PlaceResolveFromPoiRequest;
import com.tracci.module.placecontent.dto.response.PlaceAdminSearchItem;

import java.util.List;

/**
 * 管理端地点解析服务：为"业务库中尚无对应 Place 的景点"提供高德候选与受信解析。
 * 独立于 {@link PlaceContentService} 以避免 place ↔ placecontent 构造器循环依赖
 * （PlaceServiceImpl 已依赖 PlaceContentService 做公开详情聚合）。
 */
public interface PlaceAdminResolveService {

    /**
     * 高德 POI 建议：按关键词代理高德搜索，仅返回候选，不落库。
     */
    List<LocationPoiDTO> listPoiSuggestions(String keyword, String city);

    /**
     * 从高德 POI 解析/创建业务 Place（服务端核验，命中已有 (AMAP, poiId) 身份即复用），
     * 返回附内容资产状态的管理端条目，供运营直接进入内容创建。
     */
    PlaceAdminSearchItem resolvePlaceFromPoi(PlaceResolveFromPoiRequest request);

    /**
     * 管理端直接创建城市（DESTINATION）或手工 POI。
     * 同名 ACTIVE DESTINATION 直接复用，不重复建城。
     */
    PlaceAdminSearchItem createPlace(PlaceAdminCreateRequest request);

    /**
     * 管理端更新地点身份字段。DESTINATION 改名默认同步该城 POI 的 city_name。
     */
    PlaceAdminSearchItem updatePlace(String placeId, PlaceAdminUpdateRequest request);

    PlaceAdminSearchItem getPlace(String placeId);
}
