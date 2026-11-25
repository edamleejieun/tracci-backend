package com.tracci.integration.wechat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
@EnableConfigurationProperties(WechatProperties.class)
public class WechatIntegrationConfiguration {

    @Bean
    public WechatMiniClient wechatMiniClient(
            WechatProperties properties,
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper) {
        WechatProperties.MiniProgram miniProgram = properties.getMiniProgram();
        if (miniProgram.isMock()) {
            return code -> new WechatSession("mock_" + Math.abs(code.hashCode()), null, null);
        }
        int timeoutMs = Math.max(miniProgram.getTimeoutMs(), 1000);
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(timeoutMs));
        requestFactory.setReadTimeout(Duration.ofMillis(timeoutMs));
        return new WechatMiniClientImpl(
                restClientBuilder.requestFactory(requestFactory),
                miniProgram,
                objectMapper);
    }

    @Bean
    public WechatContentSecurityClient wechatContentSecurityClient(
            WechatProperties properties,
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper) {
        WechatProperties.MiniProgram miniProgram = properties.getMiniProgram();
        if (miniProgram.isMock() || !miniProgram.isContentSecurityEnabled()) {
            return new WechatContentSecurityClient() {
                @Override
                public void checkText(String openid, String content) {
                }

                @Override
                public void checkImage(String openid, byte[] imageBytes) {
                }
            };
        }
        int timeoutMs = Math.max(miniProgram.getTimeoutMs(), 1000);
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(timeoutMs));
        requestFactory.setReadTimeout(Duration.ofMillis(timeoutMs));
        return new WechatContentSecurityClientImpl(
                restClientBuilder.requestFactory(requestFactory),
                miniProgram,
                objectMapper);
    }

    @Bean
    public WechatAppClient wechatAppClient(
            WechatProperties properties,
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper) {
        WechatProperties.App app = properties.getApp();
        if (app.isMock()) {
            return code -> {
                int hash = Math.abs(code.hashCode());
                return new WechatOAuthResult(
                        "app_mock_openid_" + hash,
                        "app_mock_unionid_" + (hash % 1000),
                        "微信旅人",
                        null);
            };
        }
        int timeoutMs = Math.max(app.getTimeoutMs(), 1000);
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(timeoutMs));
        requestFactory.setReadTimeout(Duration.ofMillis(timeoutMs));
        return new WechatAppClientImpl(
                restClientBuilder.requestFactory(requestFactory),
                app,
                objectMapper);
    }
}
