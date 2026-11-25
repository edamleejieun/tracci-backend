package com.tracci.module.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ChangePasswordReq(
        @NotBlank(message = "旧密码不能为空")
        String oldPassword,

        @NotBlank(message = "新密码不能为空")
        @Pattern(
                regexp = "^(?=.*[A-Za-z])(?=.*\\d).{8,30}$",
                message = "新密码须为 8-30 位且同时包含字母与数字")
        String newPassword
) {
}
