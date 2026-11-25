package com.tracci.integration.sms;

import com.aliyun.dypnsapi20170525.models.SendSmsVerifyCodeRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AliyunPnvsSmsClientTest {

    @Test
    void buildSendVerificationCodeRequest_usesGiftedSignAndMatchingTemplate() {
        AliyunPnvsProperties properties = new AliyunPnvsProperties();
        properties.setAccessKeyId("test-access-key-id");
        properties.setAccessKeySecret("test-access-key-secret");
        properties.setSchemeName("默认方案");
        properties.setSignName("系统赠送签名");
        properties.setTemplateCode("100001");
        properties.setTemplateParam("{\"code\":\"##code##\",\"min\":\"5\"}");
        AliyunPnvsSmsClient client = new AliyunPnvsSmsClient(properties);

        SendSmsVerifyCodeRequest request =
                client.buildSendVerificationCodeRequest("+8613800138000");

        assertEquals("86", request.getCountryCode());
        assertEquals("13800138000", request.getPhoneNumber());
        assertEquals("默认方案", request.getSchemeName());
        assertEquals("系统赠送签名", request.getSignName());
        assertEquals("100001", request.getTemplateCode());
        assertEquals("{\"code\":\"##code##\",\"min\":\"5\"}", request.getTemplateParam());
        assertEquals(6L, request.getCodeLength());
        assertEquals(300L, request.getValidTime());
        assertEquals(60L, request.getInterval());
    }
}
