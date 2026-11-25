package com.tracci.common.response;

import java.util.List;

/**
 * 分页响应体
 *
 * <pre>
 * {
 *   "list": [...],
 *   "total": 42,
 *   "page": 1,
 *   "pageSize": 20
 * }
 * </pre>
 *
 * 使用方式：ApiResponse.ok(PageResponse.of(list, total, page, pageSize))
 */
public record PageResponse<T>(List<T> list, long total, int page, int pageSize) {

    public static <T> PageResponse<T> of(List<T> list, long total, int page, int pageSize) {
        return new PageResponse<>(list, total, page, pageSize);
    }
}
