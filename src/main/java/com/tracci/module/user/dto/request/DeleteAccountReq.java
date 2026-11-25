package com.tracci.module.user.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * 用户主动注销账号。
 */
public record DeleteAccountReq(
        @NotBlank(message = "confirmText 不能为空")
        String confirmText,
        String reason,
        String password
) {
}
