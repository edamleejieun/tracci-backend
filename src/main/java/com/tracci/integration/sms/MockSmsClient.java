package com.tracci.integration.sms;

import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

/**
 * 开发环境短信 Mock：不落真实通道，验证码以 Redis 中写入为准。
 * 本机号码一键登录在 mock 模式下直接返回 accessToken 本身（测试用）。
 */
@Slf4j
public class MockSmsClient implements SmsClient {

    @Override
    public void sendVerificationCode(String phone, String code) {
        log.info("[MockSms] would send code to phone={}, code={}", phone, code);
    }

    @Override
    public String getMobileByAccessToken(String accessToken) {
        log.warn("[MockSms] one-tap login in mock mode, accessToken={}", accessToken);
        // mock 模式下无法从运营商获取真实手机号，返回一个测试号码
        // 生产环境应使用 AliyunPnvsSmsClient
        return "+8613800138000";
    }
}
