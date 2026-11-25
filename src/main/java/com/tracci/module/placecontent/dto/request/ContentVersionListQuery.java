package com.tracci.module.placecontent.dto.request;

/**
 * 内容版本列表查询条件。
 *
 * @param status   按状态过滤（可为空）
 * @param placeId  按地点过滤（可为空）
 * @param reviewDue 仅返回 next_review_at <= now() 的 PUBLISHED 版本
 * @param page     页码（从 1 开始）
 * @param pageSize 每页大小
 */
public record ContentVersionListQuery(
        String status,
        String placeId,
        Boolean reviewDue,
        int page,
        int pageSize
) {
}
