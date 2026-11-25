package com.tracci.module.placecontent.dto.request;

/**
 * 管理端地点搜索条件（用于内容管理的地点发现）。
 *
 * @param keyword      名称/地址模糊匹配；若形如 UUID 则同时按 places.id 精确匹配
 * @param placeId      业务 Place ID 精确过滤（可选）
 * @param cityName     城市名模糊匹配
 * @param placeScope   地点范围：DESTINATION / POI
 * @param category     地点分类：PlaceCategory 枚举值
 * @param contentState 内容状态过滤：PUBLISHED（已有发布版本）/ NONE（无发布版本）
 * @param page         页码（从 1 开始）
 * @param pageSize     每页大小
 */
public record PlaceAdminSearchQuery(
        String keyword,
        String placeId,
        String cityName,
        String placeScope,
        String category,
        String contentState,
        int page,
        int pageSize
) {
}
