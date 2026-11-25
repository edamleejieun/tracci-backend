package com.tracci.integration.sms;

/**
 * 手机号认证适配：短信验证码绑定 + App 本机号码一键登录。
 */
public interface SmsClient {

    void sendVerificationCode(String phone, String code);

    default boolean storesVerificationCodeLocally() {
        return true;
    }

    default boolean checkVerificationCode(String phone, String code) {
        throw new UnsupportedOperationException("Remote SMS verification is not supported");
    }

    default String getMobileByAccessToken(String accessToken) {
        throw new UnsupportedOperationException("One-tap mobile login is not supported");
    }
}
