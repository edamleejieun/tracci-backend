package com.tracci.module.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record WechatAppLoginReq(
        @NotBlank(message = "code 不能为空")
        String code,

        @Size(max = 32)
        String channel
) {
}
