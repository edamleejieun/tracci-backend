package com.tracci.integration.email;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * 邮件发送集成装配。
 * <p>
 * 根据 {@code tracci.integration.email.mock} 配置决定注入 Mock 还是腾讯云 SES 实现。
 */
@Slf4j
@Configuration
public class EmailIntegrationConfiguration {

    @Bean
    public EmailClient emailClient(TencentEmailProperties properties) {
        if (properties.isMock()) {
            log.info("Email client running in mock mode");
            return new MockEmailClient();
        }
        validateRealEmailProperties(properties);
        log.info("Email client initialized with Tencent SES: region={} fromEmail={} fromName={}",
                properties.getRegion(), properties.getFromEmail(), properties.getFromName());
        return new TencentEmailClient(properties);
    }

    private void validateRealEmailProperties(TencentEmailProperties properties) {
        if (!StringUtils.hasText(properties.getSecretId())
                || !StringUtils.hasText(properties.getSecretKey())) {
            throw new IllegalStateException(
                    "Tencent SES secret-id/secret-key must be configured when email.mock=false");
        }
        if (!StringUtils.hasText(properties.getFromEmail())) {
            throw new IllegalStateException(
                    "Tencent SES from-email must be configured when email.mock=false");
        }
        if (!StringUtils.hasText(properties.getResetPasswordTemplateId())) {
            throw new IllegalStateException(
                    "Tencent SES reset-password-template-id must be configured when email.mock=false");
        }
        if (!StringUtils.hasText(properties.getResetPasswordSubject())) {
            throw new IllegalStateException(
                    "Tencent SES reset-password-subject must be configured when email.mock=false");
        }
        if (!StringUtils.hasText(properties.getBindEmailTemplateId())) {
            throw new IllegalStateException(
                    "Tencent SES bind-email-template-id must be configured when email.mock=false");
        }
        if (!StringUtils.hasText(properties.getBindEmailSubject())) {
            throw new IllegalStateException(
                    "Tencent SES bind-email-subject must be configured when email.mock=false");
        }
    }
}
