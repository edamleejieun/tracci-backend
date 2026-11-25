package com.tracci.module.placecontent.service;

import com.tracci.common.response.PageResponse;
import com.tracci.module.placecontent.dto.request.ContentVersionCreateRequest;
import com.tracci.module.placecontent.dto.request.ContentVersionListQuery;
import com.tracci.module.placecontent.dto.request.ContentVersionUpdateRequest;
import com.tracci.module.placecontent.dto.request.PlaceAdminSearchQuery;
import com.tracci.module.placecontent.dto.response.ContentVersionResponse;
import com.tracci.module.placecontent.dto.response.MediaAssetDto;
import com.tracci.module.placecontent.dto.response.PlaceAdminSearchItem;
import com.tracci.module.placecontent.dto.response.PublishedContentDto;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 地点内容版本管理服务。
 * 负责内容版本的状态机转换、发布事务和读取查询。
 */
public interface PlaceContentService {

    /**
     * 创建内容版本草稿。
     */
    ContentVersionResponse createDraft(String placeId, ContentVersionCreateRequest request);

    /**
     * 在短事务内持久化 AI 生成草稿，并以 Place 行锁和数据库唯一约束保证幂等。
     */
    ContentVersionResponse createGeneratedDraft(
            String placeId,
            ContentVersionCreateRequest request,
            String sourceDigest,
            String promptVersion,
            String modelProvider,
            String modelName,
            String workflowId,
            String generationIdempotencyKey,
            boolean regenerate);

    /**
     * 更新草稿字段。仅 DRAFT 状态可更新。
     */
    ContentVersionResponse updateDraft(String versionId, ContentVersionUpdateRequest request);

    /**
     * 提交审核。DRAFT → PENDING_REVIEW。
     */
    ContentVersionResponse submitReview(String versionId, String operatorId);

    /**
     * 驳回审核。PENDING_REVIEW → DRAFT。
     */
    ContentVersionResponse rejectReview(String versionId, String operatorId);

    /**
     * 事务发布。PENDING_REVIEW → PUBLISHED，旧 PUBLISHED → ARCHIVED。
     */
    ContentVersionResponse publishVersion(
            String versionId,
            String reviewerId,
            String idempotencyKey,
            String requestId);

    /**
     * 将已发布版本标为不可信下线。PUBLISHED → OUTDATED。
     */
    ContentVersionResponse markOutdated(String versionId, String operatorId);

    /**
     * 归档版本（不可对 PUBLISHED 直接归档，应先 outdate 或由新版本发布顶替）。
     */
    ContentVersionResponse archiveVersion(String versionId);

    /**
     * 从历史版本克隆新草稿。默认 generationType=ROLLBACK 并写入 rollbackFromVersionId。
     */
    ContentVersionResponse cloneAsDraft(String versionId, String operatorId);

    /**
     * 查询内容版本详情（含来源）。
     */
    ContentVersionResponse getVersion(String versionId);

    /**
     * 分页查询内容版本列表。
     */
    PageResponse<ContentVersionResponse> listVersions(ContentVersionListQuery query);

    /**
     * 查询指定地点当前 PUBLISHED 的编辑内容与元信息。无 PUBLISHED 时返回 null。
     */
    PublishedContentDto findPublishedContent(String placeId);

    /**
     * 查询指定地点当前 APPROVED 且未过期、可解析展示的媒体资产。无可用媒体时返回空列表。
     */
    List<MediaAssetDto> findApprovedMedia(String placeId);

    /**
     * 批量查询各地点封面媒体：{@code sort_order} 最小且 URL 可解析的 APPROVED 项。
     * 无可用封面的地点不出现在结果中。
     */
    Map<String, MediaAssetDto> findCoverMediaByPlaceIds(Collection<String> placeIds);

    /**
     * 管理端按名称/地址/城市等条件搜索业务 Place，
     * 返回项附 Provider 引用与内容资产状态，用于内容管理的地点发现与消歧。
     */
    PageResponse<PlaceAdminSearchItem> searchPlacesForAdmin(PlaceAdminSearchQuery query);
}
