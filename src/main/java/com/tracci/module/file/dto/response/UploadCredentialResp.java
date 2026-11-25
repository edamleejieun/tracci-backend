package com.tracci.module.file.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record UploadCredentialResp(
        String fileId,
        String objectKey,
        String uploadUrl,
        String method,
        Map<String, String> headers,
        Map<String, String> formData,
        String fileFieldName,
        String expiresAt
) {
}
