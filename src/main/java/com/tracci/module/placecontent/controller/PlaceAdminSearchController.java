package com.tracci.module.placecontent.controller;

import com.tracci.common.context.LoginUserContext;
import com.tracci.common.exception.ForbiddenException;
import com.tracci.common.response.ApiResponse;
import com.tracci.common.response.PageResponse;
import com.tracci.module.location.dto.response.LocationPoiDTO;
import com.tracci.module.placecontent.dto.request.PlaceAdminCreateRequest;
import com.tracci.module.placecontent.dto.request.PlaceAdminSearchQuery;
import com.tracci.module.placecontent.dto.request.PlaceAdminUpdateRequest;
import com.tracci.module.placecontent.dto.request.PlaceResolveFromPoiRequest;
import com.tracci.module.placecontent.dto.response.PlaceAdminSearchItem;
import com.tracci.module.placecontent.service.PlaceAdminResolveService;
import com.tracci.module.placecontent.service.PlaceContentService;
import com.tracci.service.AdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 管理端地点搜索/解析内部 API。
 * 解决内容管理中"从名称到业务 Place UUID"的地点发现入口；
 * 业务上 POI 搜索（/api/v1/locations/poi/search）只代理高德、不产生业务 Place，
 * 管理员无法通过名称定位 placeId，此接口补齐该缺口（含库里尚无 Place 时的高德候选与解析）。
 */
@RestController
@RequestMapping("/internal/v1/places")
@RequiredArgsConstructor
public class PlaceAdminSearchController {

    private final PlaceContentService placeContentService;
    private final PlaceAdminResolveService placeAdminResolveService;
    private final AdminService adminService;

    /**
     * 按名称/地址/城市搜索业务 Place，附 Provider 引用与内容资产状态。
     */
    @GetMapping
    public ApiResponse<PageResponse<PlaceAdminSearchItem>> searchPlaces(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String placeId,
            @RequestParam(required = false) String cityName,
            @RequestParam(required = false) String placeScope,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String contentState,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        requireAdmin();
        PlaceAdminSearchQuery query = new PlaceAdminSearchQuery(
                keyword, placeId, cityName, placeScope, category, contentState, page, pageSize);
        return ApiResponse.ok(placeContentService.searchPlacesForAdmin(query));
    }

    /**
     * 高德 POI 建议：为业务库中尚无对应 Place 的地点提供候选（代理高德搜索，不落库）。
     */
    @GetMapping("/poi-suggestions")
    public ApiResponse<List<LocationPoiDTO>> poiSuggestions(
            @RequestParam String keyword,
            @RequestParam(required = false) String city) {
        requireAdmin();
        return ApiResponse.ok(placeAdminResolveService.listPoiSuggestions(keyword, city));
    }

    /**
     * 从高德 POI 解析/创建业务 Place：服务端核验 poiId，命中已有身份复用同一 Place。
     * 返回项附 Provider 引用与内容状态，运营确认后即可进入内容创建。
     */
    @PostMapping("/resolve-from-poi")
    public ApiResponse<PlaceAdminSearchItem> resolveFromPoi(
            @Valid @RequestBody PlaceResolveFromPoiRequest request) {
        requireAdmin();
        return ApiResponse.ok(placeAdminResolveService.resolvePlaceFromPoi(request));
    }

    /**
     * 管理端直接创建城市 DESTINATION，或手工创建挂在某城市下的 POI。
     */
    @PostMapping
    public ApiResponse<PlaceAdminSearchItem> createPlace(
            @Valid @RequestBody PlaceAdminCreateRequest request) {
        requireAdmin();
        return ApiResponse.ok(placeAdminResolveService.createPlace(request));
    }

    @GetMapping("/{placeId}")
    public ApiResponse<PlaceAdminSearchItem> getPlace(@PathVariable String placeId) {
        requireAdmin();
        return ApiResponse.ok(placeAdminResolveService.getPlace(placeId));
    }

    @PutMapping("/{placeId}")
    public ApiResponse<PlaceAdminSearchItem> updatePlace(
            @PathVariable String placeId,
            @Valid @RequestBody PlaceAdminUpdateRequest request) {
        requireAdmin();
        return ApiResponse.ok(placeAdminResolveService.updatePlace(placeId, request));
    }

    private void requireAdmin() {
        LoginUserContext.requireUserId();
        if (!adminService.isCurrentUserAdmin()) {
            throw new ForbiddenException("仅管理员可搜索地点");
        }
    }
}
