package com.tracci.integration.sms;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Slf4j
@Configuration
public class SmsIntegrationConfiguration {

    @Bean
    public SmsClient smsClient(SmsProperties properties, AliyunPnvsProperties aliyunProperties) {
        if (properties.isMock()) {
            log.info("SMS client running in mock mode");
            return new MockSmsClient();
        }
        validateAliyunPnvsProperties(aliyunProperties);
        log.info("SMS client initialized with Aliyun PNVS: endpoint={}", aliyunProperties.getEndpoint());
        return new AliyunPnvsSmsClient(aliyunProperties);
    }

    private void validateAliyunPnvsProperties(AliyunPnvsProperties properties) {
        if (!StringUtils.hasText(properties.getAccessKeyId())
                || !StringUtils.hasText(properties.getAccessKeySecret())) {
            throw new IllegalStateException(
                    "Aliyun PNVS access-key-id/access-key-secret must be configured");
        }
        if (!StringUtils.hasText(properties.getEndpoint())) {
            throw new IllegalStateException("Aliyun PNVS endpoint must be configured");
        }
        if (!StringUtils.hasText(properties.getSignName())) {
            throw new IllegalStateException("Aliyun PNVS gifted sign-name must be configured");
        }
        if (!StringUtils.hasText(properties.getTemplateCode())) {
            throw new IllegalStateException("Aliyun PNVS gifted template-code must be configured");
        }
        if (!StringUtils.hasText(properties.getTemplateParam())
                || !properties.getTemplateParam().contains("##code##")) {
            throw new IllegalStateException(
                    "Aliyun PNVS template-param must contain ##code## for remote verification");
        }
    }
}
