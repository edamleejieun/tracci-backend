package com.tracci.module.file.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record UploadCredentialReq(
        @NotBlank(message = "业务类型不能为空")
        @Size(max = 32, message = "业务类型长度不能超过 32")
        String businessType,

        @Size(max = 36, message = "业务 ID 长度不能超过 36")
        String businessId,

        @NotBlank(message = "文件名不能为空")
        @Size(max = 255, message = "文件名长度不能超过 255")
        String fileName,

        @NotBlank(message = "Content-Type 不能为空")
        @Size(max = 64, message = "Content-Type 长度不能超过 64")
        String contentType,

        @Positive(message = "文件大小必须大于 0")
        long size,

        @Size(max = 8, message = "上传方式长度不能超过 8")
        String uploadMethod
) {
    public UploadCredentialReq(
            String businessType,
            String businessId,
            String fileName,
            String contentType,
            long size) {
        this(businessType, businessId, fileName, contentType, size, null);
    }
}
