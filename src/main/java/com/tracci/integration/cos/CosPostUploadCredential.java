package com.tracci.integration.cos;

import java.util.Map;

public record CosPostUploadCredential(
        String uploadUrl,
        Map<String, String> formData,
        String fileFieldName
) {
}
