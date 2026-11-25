package com.tracci.module.auth.dto.response;

/**
 * 发送邮箱验证码响应
 */
public record EmailCodeSendResp(int expireSeconds, int retryAfterSeconds) {
}
