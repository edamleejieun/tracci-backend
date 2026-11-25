package com.tracci.module.auth.enums;

/**
 * 身份提供方枚举，与 user_auth_identities.provider 对齐。
 * <p>
 * EMAIL / PHONE 基于主账号体系；WECHAT_MINI / WECHAT_APP 为微信跨端；
 * APPLE 为 Sign in with Apple 预留。
 */
public enum AuthProvider {
    EMAIL,
    PHONE,
    WECHAT_MINI,
    WECHAT_APP,
    APPLE
}
