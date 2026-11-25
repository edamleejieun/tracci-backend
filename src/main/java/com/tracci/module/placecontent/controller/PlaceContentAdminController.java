package com.tracci.module.placecontent.controller;

import com.tracci.common.context.LoginUserContext;
import com.tracci.common.exception.ForbiddenException;
import com.tracci.common.response.ApiResponse;
import com.tracci.common.response.PageResponse;
import com.tracci.module.placecontent.dto.request.ContentVersionCreateRequest;
import com.tracci.module.placecontent.dto.request.ContentVersionListQuery;
import com.tracci.module.placecontent.dto.request.ContentVersionUpdateRequest;
import com.tracci.module.placecontent.dto.response.ContentVersionResponse;
import com.tracci.module.placecontent.service.PlaceContentService;
import com.tracci.service.AdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 地点内容版本管理内部 API。
 * 所有接口需要 JWT + 管理员白名单。
 */
@RestController
@RequestMapping("/internal/v1")
@RequiredArgsConstructor
public class PlaceContentAdminController {

    private final PlaceContentService placeContentService;
    private final AdminService adminService;

    /**
     * 查询内容版本列表（分页）。
     */
    @GetMapping("/place-content-versions")
    public ApiResponse<PageResponse<ContentVersionResponse>> listVersions(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String placeId,
            @RequestParam(required = false) Boolean reviewDue,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        requireAdmin();
        ContentVersionListQuery query = new ContentVersionListQuery(status, placeId, reviewDue, page, pageSize);
        return ApiResponse.ok(placeContentService.listVersions(query));
    }

    /**
     * 查询内容版本详情（含来源事实）。
     */
    @GetMapping("/place-content-versions/{versionId}")
    public ApiResponse<ContentVersionResponse> getVersion(@PathVariable String versionId) {
        requireAdmin();
        return ApiResponse.ok(placeContentService.getVersion(versionId));
    }

    /**
     * 为指定地点创建内容版本草稿。
     */
    @PostMapping("/places/{placeId}/content-versions")
    public ApiResponse<ContentVersionResponse> createDraft(
            @PathVariable String placeId,
            @Valid @RequestBody ContentVersionCreateRequest request) {
        requireAdmin();
        return ApiResponse.ok(placeContentService.createDraft(placeId, request));
    }

    /**
     * 更新草稿字段与来源。
     */
    @PutMapping("/place-content-versions/{versionId}")
    public ApiResponse<ContentVersionResponse> updateDraft(
            @PathVariable String versionId,
            @Valid @RequestBody ContentVersionUpdateRequest request) {
        requireAdmin();
        return ApiResponse.ok(placeContentService.updateDraft(versionId, request));
    }

    /**
     * 提交审核。DRAFT → PENDING_REVIEW。
     */
    @PostMapping("/place-content-versions/{versionId}/submit-review")
    public ApiResponse<ContentVersionResponse> submitReview(@PathVariable String versionId) {
        requireAdmin();
        String operatorId = LoginUserContext.requireUserId();
        return ApiResponse.ok(placeContentService.submitReview(versionId, operatorId));
    }

    /**
     * 驳回审核。PENDING_REVIEW → DRAFT。
     */
    @PostMapping("/place-content-versions/{versionId}/reject")
    public ApiResponse<ContentVersionResponse> rejectReview(@PathVariable String versionId) {
        requireAdmin();
        String operatorId = LoginUserContext.requireUserId();
        return ApiResponse.ok(placeContentService.rejectReview(versionId, operatorId));
    }

    /**
     * 事务发布。PENDING_REVIEW → PUBLISHED，旧 PUBLISHED → ARCHIVED。
     */
    @PostMapping("/place-content-versions/{versionId}/publish")
    public ApiResponse<ContentVersionResponse> publish(
            @PathVariable String versionId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId) {
        requireAdmin();
        String reviewerId = LoginUserContext.requireUserId();
        return ApiResponse.ok(placeContentService.publishVersion(
                versionId, reviewerId, idempotencyKey, requestId));
    }

    /**
     * 将已发布版本标记为 OUTDATED（用户端不可见）。
     */
    @PostMapping("/place-content-versions/{versionId}/outdate")
    public ApiResponse<ContentVersionResponse> markOutdated(@PathVariable String versionId) {
        requireAdmin();
        String operatorId = LoginUserContext.requireUserId();
        return ApiResponse.ok(placeContentService.markOutdated(versionId, operatorId));
    }

    /**
     * 归档版本（非 PUBLISHED）。
     */
    @PostMapping("/place-content-versions/{versionId}/archive")
    public ApiResponse<ContentVersionResponse> archive(@PathVariable String versionId) {
        requireAdmin();
        return ApiResponse.ok(placeContentService.archiveVersion(versionId));
    }

    /**
     * 从历史版本克隆新 DRAFT（generationType=ROLLBACK）。
     */
    @PostMapping("/place-content-versions/{versionId}/clone")
    public ApiResponse<ContentVersionResponse> cloneAsDraft(@PathVariable String versionId) {
        requireAdmin();
        String operatorId = LoginUserContext.requireUserId();
        return ApiResponse.ok(placeContentService.cloneAsDraft(versionId, operatorId));
    }

    private void requireAdmin() {
        LoginUserContext.requireUserId();
        if (!adminService.isCurrentUserAdmin()) {
            throw new ForbiddenException("仅管理员可操作地点内容版本");
        }
    }
}
