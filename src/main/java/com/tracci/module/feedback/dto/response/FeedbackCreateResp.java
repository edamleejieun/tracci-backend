package com.tracci.module.feedback.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record FeedbackCreateResp(
        String id,
        String status,
        String createdAt
) {
}
