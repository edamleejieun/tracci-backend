package com.tracci.module.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record WechatMiniLoginReq(
        @NotBlank(message = "code 不能为空")
        String code,

        @Size(max = 4096)
        String encryptedData,

        @Size(max = 512)
        String iv
) {
}
