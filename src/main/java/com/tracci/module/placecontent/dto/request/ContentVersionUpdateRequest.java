package com.tracci.module.placecontent.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * 更新内容版本草稿请求。仅 DRAFT 状态可更新，所有字段可选。
 * sources 为 null 时不修改来源；非 null 时整表替换该版本的来源列表。
 */
public record ContentVersionUpdateRequest(
        String introduction,
        String highlightsJson,

        @Min(value = 1, message = "recommendedDurationMinutes 最小为 1")
        @Max(value = 1440, message = "recommendedDurationMinutes 最大为 1440")
        Integer recommendedDurationMinutes,

        String visitTipsJson,
        String bestVisitTime,
        String reservationTips,
        String notice,

        List<@NotNull @Valid SourceFactRequest> sources,

        java.time.LocalDateTime nextReviewAt
) {
}
