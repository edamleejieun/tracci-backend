package com.tracci.module.auth.enums;

/**
 * 邮箱验证码用途，用于 Redis key 隔离和业务校验。
 */
public enum EmailCodePurpose {
    RESET_PASSWORD,
    BIND_EMAIL
}
