package com.tracci.module.placecontent.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 内容版本详情响应（internal admin 使用）。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ContentVersionResponse(
        String id,
        String placeId,
        Integer versionNo,
        String locale,
        String introduction,
        List<String> highlights,
        Integer recommendedDurationMinutes,
        List<String> visitTips,
        String bestVisitTime,
        String reservationTips,
        String notice,
        String generationType,
        boolean aiAssisted,
        String modelProvider,
        String modelName,
        String promptVersion,
        String status,
        String reviewedBy,
        LocalDateTime reviewedAt,
        LocalDateTime verifiedAt,
        LocalDateTime nextReviewAt,
        LocalDateTime publishedAt,
        LocalDateTime archivedAt,
        String contentHash,
        String generationIdempotencyKey,
        String sourceWorkflowId,
        String publishIdempotencyKey,
        String publishRequestId,
        String previousPublishedVersionId,
        String previousPublishedContentHash,
        String rollbackFromVersionId,
        List<SourceFactsDto> sources,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
