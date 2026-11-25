package com.tracci.module.auth.support;

import com.tracci.module.auth.enums.EmailCodePurpose;

/**
 * Auth 模块 Redis Key 约定
 */
public final class AuthRedisKeys {

    private AuthRedisKeys() {
    }

    // ---- 邮箱验证码 ----

    /**
     * 邮箱验证码存储 key，格式：auth:email_code:{purpose}:{email}
     */
    public static String emailCode(EmailCodePurpose purpose, String email) {
        return "auth:email_code:" + purpose.name() + ":" + email.toLowerCase();
    }

    /**
     * 邮箱验证码发送冷却 key，格式：auth:email_cooldown:{purpose}:{email}
     */
    public static String emailCooldown(EmailCodePurpose purpose, String email) {
        return "auth:email_cooldown:" + purpose.name() + ":" + email.toLowerCase();
    }

    // ---- 短信验证码（保留供后续手机号功能启用） ----

    public static String smsCode(String phone) {
        return "tracci:sms:code:" + phone;
    }

    public static String smsCooldown(String phone) {
        return "tracci:sms:cooldown:" + phone;
    }

    // ---- 手机号绑定验证码 ----

    /**
     * 手机号绑定验证码 key，格式：tracci:phone:bind:code:{phone}
     */
    public static String phoneBindCode(String phone) {
        return "tracci:phone:bind:code:" + phone;
    }

    /**
     * 手机号绑定验证码冷却 key，格式：tracci:phone:bind:cooldown:{phone}
     */
    public static String phoneBindCooldown(String phone) {
        return "tracci:phone:bind:cooldown:" + phone;
    }

    // ---- Token ----

    public static String refreshToken(String userId) {
        return "tracci:refresh:" + userId;
    }

    // ---- 登录失败计数 ----

    /**
     * 登录失败计数 key，支持邮箱和测试账号标识
     */
    public static String loginFail(String identifier) {
        return "tracci:login:fail:" + identifier.toLowerCase();
    }
}
