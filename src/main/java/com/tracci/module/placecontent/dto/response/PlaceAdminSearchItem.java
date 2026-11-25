package com.tracci.module.placecontent.dto.response;

import java.math.BigDecimal;
import java.util.List;

/**
 * 管理端地点搜索结果项。
 * 在 Place 基础信息之上附内容资产状态与 Provider 引用，
 * 帮助运营在"同一地点多条 Place"的候选中完成消歧（优先选择 AMAP 已核验记录）。
 */
public record PlaceAdminSearchItem(
        String id,
        String canonicalName,
        String placeScope,
        String category,
        String createdSource,
        String status,
        String mergedIntoId,
        String address,
        String cityName,
        String country,
        BigDecimal latitude,
        BigDecimal longitude,
        List<ProviderRefDto> providerRefs,
        boolean hasPublishedContent,
        String publishedVersionId,
        Integer publishedVersionNo,
        int contentVersionCount
) {
}
