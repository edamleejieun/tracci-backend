package com.tracci.integration.email;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EmailIntegrationConfigurationTest {

    private final EmailIntegrationConfiguration configuration = new EmailIntegrationConfiguration();

    @Test
    void emailClient_mockMode_usesMockClient() {
        TencentEmailProperties properties = new TencentEmailProperties();
        properties.setMock(true);

        EmailClient client = configuration.emailClient(properties);

        assertInstanceOf(MockEmailClient.class, client);
    }

    @Test
    void emailClient_realMode_missingTemplateId_failsFast() {
        TencentEmailProperties properties = new TencentEmailProperties();
        properties.setMock(false);
        properties.setSecretId("secret");
        properties.setSecretKey("key");
        properties.setFromEmail("noreply@mail.example.com");
        properties.setResetPasswordSubject("【趣溪】找回密码验证码");

        assertThrows(IllegalStateException.class, () -> configuration.emailClient(properties));
    }

    @Test
    void emailClient_realMode_withRequiredConfig_usesTencentClient() {
        TencentEmailProperties properties = new TencentEmailProperties();
        properties.setMock(false);
        properties.setSecretId("secret");
        properties.setSecretKey("key");
        properties.setFromEmail("noreply@mail.example.com");
        properties.setResetPasswordTemplateId("100091");
        properties.setResetPasswordSubject("【趣溪】找回密码验证码");
        properties.setBindEmailTemplateId("100092");
        properties.setBindEmailSubject("【趣溪】绑定邮箱验证码");

        EmailClient client = configuration.emailClient(properties);

        assertInstanceOf(TencentEmailClient.class, client);
    }
}
