package com.tracci.integration.tencentwsa;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties("tracci.integration.tencent-wsa")
public class TencentWsaProperties {

    private boolean enabled = false;
    /**
     * @deprecated Tencent Cloud WSA SearchPro uses Cloud API 3.0 SecretId/SecretKey signing.
     * This field is kept only to avoid breaking old local configuration names.
     */
    @Deprecated
    private String apiKey;
    private String secretId;
    private String secretKey;
    private String endpoint = "https://wsa.tencentcloudapi.com";
    private String service = "wsa";
    private String action = "SearchPro";
    private String version = "2025-05-08";
    /**
     * Optional SearchPro mode. Leave null to use Tencent Cloud's API default.
     */
    private Integer mode;
    private int defaultLimit = 10;
    private int timeoutMs = 5000;
}
