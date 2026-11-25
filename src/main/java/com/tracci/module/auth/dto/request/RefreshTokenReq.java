package com.tracci.module.auth.dto.request;

import jakarta.validation.constraints.NotBlank;

public record RefreshTokenReq(
        @NotBlank(message = "refreshToken 不能为空")
        String refreshToken
) {
}
