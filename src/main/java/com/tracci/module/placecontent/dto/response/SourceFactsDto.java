package com.tracci.module.placecontent.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;

/**
 * 来源事实响应（嵌套在 ContentVersionResponse 中）。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SourceFactsDto(
        String id,
        String factKey,
        String factValue,
        String sourceType,
        String sourceTitle,
        String sourceUrl,
        LocalDateTime retrievedAt,
        LocalDateTime expiresAt,
        String verificationStatus,
        String verifiedBy,
        LocalDateTime verifiedAt,
        String note
) {
}
