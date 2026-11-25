package com.tracci.module.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 发送手机号绑定验证码请求
 */
public record PhoneBindSendCodeReq(
        @NotBlank(message = "手机号不能为空")
        @Pattern(regexp = "^\\+861\\d{10}$", message = "手机号须为 +86 开头的 11 位大陆号码")
        String phone
) {
}
