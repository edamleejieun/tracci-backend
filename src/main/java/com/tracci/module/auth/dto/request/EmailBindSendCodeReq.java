package com.tracci.module.auth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 手机号一键登录账号发送邮箱绑定验证码请求。
 */
public record EmailBindSendCodeReq(
        @NotBlank(message = "邮箱不能为空")
        @Email(message = "邮箱格式不合法")
        @Size(max = 255)
        String email
) {
}
