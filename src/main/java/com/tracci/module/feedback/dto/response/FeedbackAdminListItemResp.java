package com.tracci.module.feedback.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Admin list item. Contact and sensitive user fields are omitted — only shown on detail.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record FeedbackAdminListItemResp(
        String id,
        String userId,
        String userNickname,
        String type,
        String title,
        String contentPreview,
        String status,
        String platform,
        String appVersion,
        int attachmentCount,
        String createdAt,
        String handledAt
) {
}
