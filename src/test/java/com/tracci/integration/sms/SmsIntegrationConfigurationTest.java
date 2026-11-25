package com.tracci.integration.sms;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SmsIntegrationConfigurationTest {

    private final SmsIntegrationConfiguration configuration = new SmsIntegrationConfiguration();

    @Test
    void smsClient_mockMode_usesMockClient() {
        SmsProperties properties = new SmsProperties();
        properties.setMock(true);

        SmsClient client = configuration.smsClient(properties, new AliyunPnvsProperties());

        assertInstanceOf(MockSmsClient.class, client);
    }

    @Test
    void smsClient_realMode_missingGiftedTemplate_failsFast() {
        SmsProperties properties = new SmsProperties();
        properties.setMock(false);
        AliyunPnvsProperties aliyun = baseAliyunProperties();

        assertThrows(IllegalStateException.class, () -> configuration.smsClient(properties, aliyun));
    }

    @Test
    void smsClient_realMode_staticCodeTemplateParam_failsFast() {
        SmsProperties properties = new SmsProperties();
        properties.setMock(false);
        AliyunPnvsProperties aliyun = baseAliyunProperties();
        aliyun.setSignName("系统赠送签名");
        aliyun.setTemplateCode("100001");
        aliyun.setTemplateParam("{\"code\":\"123456\",\"min\":\"5\"}");

        assertThrows(IllegalStateException.class, () -> configuration.smsClient(properties, aliyun));
    }

    @Test
    void smsClient_realMode_withGiftedTemplate_usesAliyunPnvs() {
        SmsProperties properties = new SmsProperties();
        properties.setMock(false);
        AliyunPnvsProperties aliyun = baseAliyunProperties();
        aliyun.setSignName("系统赠送签名");
        aliyun.setTemplateCode("100001");
        aliyun.setTemplateParam("{\"code\":\"##code##\",\"min\":\"5\"}");

        SmsClient client = configuration.smsClient(properties, aliyun);

        assertInstanceOf(AliyunPnvsSmsClient.class, client);
    }

    private static AliyunPnvsProperties baseAliyunProperties() {
        AliyunPnvsProperties properties = new AliyunPnvsProperties();
        properties.setAccessKeyId("test-access-key-id");
        properties.setAccessKeySecret("test-access-key-secret");
        return properties;
    }
}
