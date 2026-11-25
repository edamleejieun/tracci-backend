package com.tracci.integration.email;

import com.tracci.module.auth.enums.EmailCodePurpose;
import lombok.extern.slf4j.Slf4j;

/**
 * 邮件发送 Mock 实现，仅日志打印验证码，不真实发送邮件。
 * <p>
 * dev/test 环境使用，便于本地联调和单元测试。
 */
@Slf4j
public class MockEmailClient implements EmailClient {

    @Override
    public void sendVerificationCode(String toEmail, String code, EmailCodePurpose purpose) {
        log.info("[MockEmailClient] 发送验证码邮件: to={}, purpose={}, code={}", toEmail, purpose, code);
    }
}
