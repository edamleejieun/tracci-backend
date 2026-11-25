package com.tracci.module.feedback.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record FeedbackDetailResp(
        String id,
        String type,
        String title,
        String content,
        String contactType,
        String contact,
        String status,
        String platform,
        String appVersion,
        Object systemInfo,
        List<AttachmentResp> attachments,
        String createdAt
) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record AttachmentResp(
            String id,
            String fileId,
            String url,
            Integer sortOrder,
            String thumbnailUrl,
            String previewUrl,
            String exportUrl,
            Integer width,
            Integer height
    ) {
    }
}
