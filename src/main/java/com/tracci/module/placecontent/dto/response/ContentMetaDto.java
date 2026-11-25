package com.tracci.module.placecontent.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;

/**
 * 内容元信息。嵌入 PlaceDetailResponse，展示 AI 辅助标识与核验状态。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ContentMetaDto(
        boolean aiAssisted,
        String label,
        LocalDateTime verifiedAt,
        LocalDateTime nextReviewAt,
        boolean reviewDue
) {
}
