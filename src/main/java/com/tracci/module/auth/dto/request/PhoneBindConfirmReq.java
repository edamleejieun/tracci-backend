package com.tracci.module.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 确认绑定手机号请求
 */
public record PhoneBindConfirmReq(
        @NotBlank(message = "手机号不能为空")
        @Pattern(regexp = "^\\+861\\d{10}$", message = "手机号格式错误")
        String phone,

        @NotBlank(message = "验证码不能为空")
        @Pattern(regexp = "^\\d{6}$", message = "验证码须为 6 位数字")
        String code
) {
}
