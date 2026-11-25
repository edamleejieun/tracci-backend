package com.tracci.module.file.dto.request;

import jakarta.validation.constraints.Size;

public record ConfirmUploadReq(
        @Size(max = 64, message = "ETag 长度不能超过 64")
        String etag,

        Integer width,

        Integer height
) {
}
