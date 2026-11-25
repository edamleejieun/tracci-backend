package com.tracci.integration.sms;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties("tracci.integration.aliyun-pnvs")
public class AliyunPnvsProperties {

    private String accessKeyId;

    private String accessKeySecret;

    private String endpoint = "dypnsapi.aliyuncs.com";

    private String regionId = "cn-hangzhou";

    private String countryCode = "86";

    private String schemeName;

    /** 号码认证服务控制台提供的系统赠送签名 */
    private String signName;

    /** 与赠送签名配套的系统赠送模板 CODE */
    private String templateCode;

    /** 模板参数 JSON，验证码变量必须使用 ##code## 以支持服务端核验 */
    private String templateParam;

    private long validTimeSeconds = 300;

    private long intervalSeconds = 60;

    private long codeLength = 6;
}
