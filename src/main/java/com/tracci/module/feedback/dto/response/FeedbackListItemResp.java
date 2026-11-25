package com.tracci.module.feedback.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record FeedbackListItemResp(
        String id,
        String type,
        String title,
        String contentPreview,
        String status,
        String createdAt
) {
}
