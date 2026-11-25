package com.tracci.module.auth.dto.request;

import jakarta.validation.constraints.NotBlank;

public record PhoneOneTapLoginReq(
        @NotBlank(message = "一键登录凭证不能为空")
        String accessToken
) {
}
