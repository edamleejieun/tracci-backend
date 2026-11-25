package com.tracci.module.user.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

/**
 * 账号安全状态响应，供前端"账号与安全"页使用
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record UserSecurityResp(
        String email,
        String phone,
        boolean phoneVerified,
        boolean hasPassword,
        Map<String, Boolean> bindings
) {
}
