package com.tracci.integration.email;

import com.tracci.module.auth.enums.EmailCodePurpose;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TencentEmailClientTest {

    @Test
    void buildTemplateData_serializesCodeAsJson() {
        TencentEmailClient client = newClient();

        assertEquals("{\"code\":\"123456\"}", client.buildTemplateData("123456"));
    }

    @Test
    void resolveTemplateConfig_resetPassword_usesConfiguredTemplate() {
        TencentEmailClient client = newClient();

        TencentEmailClient.TemplateConfig config =
                client.resolveTemplateConfig(EmailCodePurpose.RESET_PASSWORD);

        assertEquals(100091L, config.templateId());
        assertEquals("【趣溪】找回密码验证码", config.subject());
    }

    @Test
    void resolveTemplateConfig_bindEmail_usesConfiguredTemplate() {
        TencentEmailClient client = newClient();

        TencentEmailClient.TemplateConfig config =
                client.resolveTemplateConfig(EmailCodePurpose.BIND_EMAIL);

        assertEquals(100092L, config.templateId());
        assertEquals("【趣溪】绑定邮箱验证码", config.subject());
    }

    private static TencentEmailClient newClient() {
        TencentEmailProperties properties = new TencentEmailProperties();
        properties.setSecretId("test-secret-id");
        properties.setSecretKey("test-secret-key");
        properties.setFromEmail("noreply@mail.example.com");
        properties.setRegion("ap-guangzhou");
        properties.setResetPasswordTemplateId("100091");
        properties.setResetPasswordSubject("【趣溪】找回密码验证码");
        properties.setBindEmailTemplateId("100092");
        properties.setBindEmailSubject("【趣溪】绑定邮箱验证码");
        return new TencentEmailClient(properties);
    }
}
