package com.tracci.module.placecontent.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 创建内容版本草稿请求。
 *
 * @param locale                     语言区域，为空时默认 zh-CN
 * @param generationType             生成方式 MANUAL / AI_ASSISTED / IMPORT / ROLLBACK
 * @param aiAssisted                 AI 辅助标识
 * @param introduction               地点介绍
 * @param highlightsJson             亮点 JSON 数组字符串
 * @param recommendedDurationMinutes 建议停留时长（分钟）
 * @param visitTipsJson              游览建议 JSON 数组字符串
 * @param bestVisitTime              最佳游览时间
 * @param reservationTips            预约提示
 * @param notice                     注意事项
 * @param sources                    字段级事实来源（发布前必须至少一条）
 * @param rollbackFromVersionId      回滚克隆来源版本 ID（generationType=ROLLBACK 时可选填写）
 * @param nextReviewAt               下次复核时间（可选）
 */
public record ContentVersionCreateRequest(
        @Size(max = 16, message = "locale 长度不能超过 16")
        String locale,

        @Size(max = 20, message = "generationType 长度不能超过 20")
        String generationType,

        Boolean aiAssisted,

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

        @Size(max = 36, message = "rollbackFromVersionId 长度不能超过 36")
        String rollbackFromVersionId,

        java.time.LocalDateTime nextReviewAt
) {
}
