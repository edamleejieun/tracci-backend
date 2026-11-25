package com.tracci.module.feedback.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Admin feedback detail. Includes contact, handler fields, and user summary.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record FeedbackAdminDetailResp(
        String id,
        String userId,
        UserSummary user,
        String type,
        String title,
        String content,
        String contactType,
        String contact,
        String status,
        String platform,
        String appVersion,
        Object systemInfo,
        List<FeedbackDetailResp.AttachmentResp> attachments,
        String handlerNote,
        String handledAt,
        String createdAt,
        String updatedAt
) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record UserSummary(
            String id,
            String nickname,
            String email,
            String phone,
            String loginType
    ) {
    }
}
