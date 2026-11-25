package com.tracci.module.savedplace.service;

import com.tracci.common.response.PageResponse;
import com.tracci.module.place.dto.response.PlaceUserContext;
import com.tracci.module.place.entity.Place;
import com.tracci.module.savedplace.dto.request.CreateSavedPlaceRequest;
import com.tracci.module.savedplace.dto.request.UpdateSavedPlaceRequest;
import com.tracci.module.savedplace.dto.response.SavedPlaceSummary;

import java.util.Collection;
import java.util.Set;

public interface SavedPlaceService {

    SavedPlaceSummary createSavedPlace(String userId, CreateSavedPlaceRequest request);

    PageResponse<SavedPlaceSummary> listSavedPlaces(
            String userId, String keyword, String placeScope, String category,
            String usage, String tripId, int page, int pageSize);

    SavedPlaceSummary updateSavedPlace(String userId, String savedPlaceId, UpdateSavedPlaceRequest request);

    void deleteSavedPlace(String userId, String savedPlaceId);

    /**
     * 解析可作为旅程目的地的收藏：校验归属 + DESTINATION scope。
     * 不写 usage，调用方落库 trip 后再 {@link #recordTripUsage}。
     */
    Place resolveForTripDestination(String userId, String savedPlaceId);

    /**
     * 解析可作为行程节点的收藏：校验归属 + POI scope + 与旅程目的地匹配。
     * 不写 usage，调用方落库 node 后再 {@link #recordNodeUsage}。
     */
    Place resolveForItineraryNode(String userId, String savedPlaceId, String tripId);

    /** 写入旅程目的地 usage。 */
    void recordTripUsage(String userId, String savedPlaceId, String tripId);

    /** 写入行程节点 usage。 */
    void recordNodeUsage(String userId, String savedPlaceId, String tripId, String nodeId);

    /** 查询当前用户对某 Place 的收藏上下文（详情页用）。 */
    PlaceUserContext buildUserContext(String userId, String placeId);

    /** 批量查询当前用户已收藏的 Place id，供探索列表 {@code isSaved}。 */
    Set<String> findSavedPlaceIds(String userId, Collection<String> placeIds);
}
