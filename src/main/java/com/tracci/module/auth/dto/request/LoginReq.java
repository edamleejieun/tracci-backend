package com.tracci.module.auth.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * 邮箱登录请求。
 * <p>
 * email 字段接收邮箱地址或测试账号标识（如 admin），
 * 正式用户必须使用邮箱，测试账号走白名单逻辑。
 */
public record LoginReq(
        @NotBlank(message = "邮箱不能为空")
        String email,

        @NotBlank(message = "密码不能为空")
        String password
) {
}
