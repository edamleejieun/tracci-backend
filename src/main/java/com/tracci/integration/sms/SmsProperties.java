package com.tracci.integration.sms;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 短信认证通用配置。真实通道统一使用阿里云号码认证服务 PNVS。
 */
@Getter
@Setter
@Component
@ConfigurationProperties("tracci.integration.sms")
public class SmsProperties {

    /** 是否使用 Mock 模式 */
    private boolean mock = true;
}
