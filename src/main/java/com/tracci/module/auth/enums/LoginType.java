package com.tracci.module.auth.enums;

/**
 * 登录方式枚举，写入 users.login_type 供响应和审计使用。
 */
public enum LoginType {
    EMAIL,
    SMS,
    PHONE_ONE_TAP,
    WECHAT_MINI,
    WECHAT_IOS,
    APPLE
}
