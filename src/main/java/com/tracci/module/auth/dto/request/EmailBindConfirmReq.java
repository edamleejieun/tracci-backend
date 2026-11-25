package com.tracci.module.auth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 手机号一键登录账号确认绑定邮箱请求。
 */
public record EmailBindConfirmReq(
        @NotBlank(message = "邮箱不能为空")
        @Email(message = "邮箱格式不合法")
        @Size(max = 255)
        String email,

        @NotBlank(message = "验证码不能为空")
        @Pattern(regexp = "^\\d{6}$", message = "验证码须为 6 位数字")
        String code,

        @NotBlank(message = "登录密码不能为空")
        @Pattern(
                regexp = "^(?=.*[A-Za-z])(?=.*\\d).{8,30}$",
                message = "密码须为 8-30 位且同时包含字母与数字")
        String password
) {
}
