package com.tracci.integration.wechat;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "tracci.wechat")
public class WechatProperties {

    private MiniProgram miniProgram = new MiniProgram();
    private App app = new App();

    @Getter
    @Setter
    public static class MiniProgram {
        private boolean mock = true;
        private String appId;
        private String appSecret;
        private String endpoint = "https://api.weixin.qq.com/sns/jscode2session";
        private String accessTokenEndpoint = "https://api.weixin.qq.com/cgi-bin/token";
        private String msgSecCheckEndpoint = "https://api.weixin.qq.com/wxa/msg_sec_check";
        private String imgSecCheckEndpoint = "https://api.weixin.qq.com/wxa/img_sec_check";
        private int timeoutMs = 3000;
        private boolean contentSecurityEnabled = true;
    }

    @Getter
    @Setter
    public static class App {
        private boolean mock = true;
        private String appId;
        private String appSecret;
        private String accessTokenEndpoint = "https://api.weixin.qq.com/sns/oauth2/access_token";
        private String userInfoEndpoint = "https://api.weixin.qq.com/sns/userinfo";
        private int timeoutMs = 3000;
    }
}
