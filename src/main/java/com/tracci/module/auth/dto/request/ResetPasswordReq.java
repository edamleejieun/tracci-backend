package com.tracci.module.auth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 邮箱验证码重置密码请求
 */
public record ResetPasswordReq(
        @NotBlank(message = "邮箱不能为空")
        @Email(message = "邮箱格式不合法")
        @Size(max = 255)
        String email,

        @NotBlank(message = "验证码不能为空")
        @Size(min = 6, max = 6, message = "验证码为 6 位数字")
        String code,

        @NotBlank(message = "新密码不能为空")
        @Pattern(
                regexp = "^(?=.*[A-Za-z])(?=.*\\d).{8,30}$",
                message = "密码须为 8-30 位且同时包含字母与数字")
        String newPassword
) {
}
