package com.tracci.module.place.service;

import com.tracci.common.response.PageResponse;
import com.tracci.module.place.dto.request.PublishedPlaceListQuery;
import com.tracci.module.place.dto.response.PlaceDetailResponse;
import com.tracci.module.place.dto.response.PlaceListItemResponse;
import com.tracci.module.place.entity.Place;
import com.tracci.module.savedplace.dto.request.LocationCandidate;

public interface PlaceService {

    /**
     * 根据 LocationCandidate 解析或创建 Place。
     * 服务端可核验的高德 POI 按 provider + providerPlaceId 全局幂等解析；
     * 高德 POI 使用服务端核验分类，传入 category 仅作为未核验来源的分类；
     * Apple / 手工快照创建独立 Place，避免未经核验的客户端 ID 污染全局身份。
     */
    Place resolveOrCreatePlace(LocationCandidate location, String placeScope, String category);

    /**
     * 获取统一地点基础详情（不含用户收藏上下文；由 Controller 组装）。
     */
    PlaceDetailResponse getPlaceDetail(String placeId);

    /**
     * 分页查询当前 locale（zh-CN）下已有 PUBLISHED 内容的 ACTIVE Place。
     * 不含用户收藏态；由 Controller 填 {@code isSaved}。
     */
    PageResponse<PlaceListItemResponse> listPublishedPlaces(PublishedPlaceListQuery query);

    /**
     * 根据 ID 获取 ACTIVE 且未删除的 Place，否则抛 PLACE_NOT_FOUND。
     */
    Place getPlaceById(String placeId);

    /**
     * 校验 placeScope 与 category 的组合约束。
     * DESTINATION 只允许 CITY/REGION；POI 不允许 CITY/REGION。
     */
    void validateScopeAndCategory(String placeScope, String category);
}
