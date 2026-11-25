package com.tracci.module.place.dto.request;

/**
 * 已发布地点列表查询。locale 固定 zh-CN，不作为入参。
 */
public record PublishedPlaceListQuery(
        String cityName,
        String placeScope,
        String category,
        int page,
        int pageSize
) {
}
