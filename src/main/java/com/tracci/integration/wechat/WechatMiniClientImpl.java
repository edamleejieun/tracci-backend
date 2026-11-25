package com.tracci.integration.wechat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tracci.common.exception.BizException;
import com.tracci.common.exception.ThirdPartyException;
import com.tracci.common.response.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.net.URI;

/**
 * 调用微信开放平台 code2session
 */
@Slf4j
public class WechatMiniClientImpl implements WechatMiniClient {

    private final RestClient restClient;
    private final WechatProperties.MiniProgram properties;
    private final ObjectMapper objectMapper;

    public WechatMiniClientImpl(
            RestClient.Builder restClientBuilder,
            WechatProperties.MiniProgram properties,
            ObjectMapper objectMapper) {
        this.restClient = restClientBuilder.build();
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public WechatSession exchangeCode(String code) {
        if (!StringUtils.hasText(properties.getAppId()) || !StringUtils.hasText(properties.getAppSecret())) {
            log.error("Wechat mini program config missing appIdPresent={} appSecretPresent={}",
                    StringUtils.hasText(properties.getAppId()), StringUtils.hasText(properties.getAppSecret()));
            throw new BizException(ErrorCode.WECHAT_LOGIN_FAILED, "服务配置异常，请联系管理员");
        }
        try {
            URI endpoint = URI.create(properties.getEndpoint());
            String raw = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .scheme(endpoint.getScheme())
                            .host(endpoint.getHost())
                            .path(endpoint.getPath())
                            .queryParam("appid", properties.getAppId())
                            .queryParam("secret", properties.getAppSecret())
                            .queryParam("js_code", code)
                            .queryParam("grant_type", "authorization_code")
                            .build())
                    .retrieve()
                    .body(String.class);
            JsonNode root = objectMapper.readTree(raw);
            if (root.hasNonNull("errcode") && root.get("errcode").asInt() != 0) {
                int errcode = root.path("errcode").asInt();
                String errmsg = root.path("errmsg").asText("wechat error");
                log.warn("Wechat mini jscode2session returned errcode={} errmsg={}", errcode, errmsg);
                throw new ThirdPartyException(ErrorCode.WECHAT_LOGIN_FAILED, friendlyWechatMessage(errcode));
            }
            String openid = root.path("openid").asText(null);
            if (openid == null || openid.isBlank()) {
                log.warn("Wechat mini jscode2session response missing openid");
                throw new ThirdPartyException(ErrorCode.WECHAT_LOGIN_FAILED);
            }
            String sessionKey = root.path("session_key").asText(null);
            String unionid = root.path("unionid").asText(null);
            return new WechatSession(openid, sessionKey, unionid);
        } catch (ThirdPartyException ex) {
            throw ex;
        } catch (BizException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("Wechat mini jscode2session request failed message={}", ex.getMessage());
            throw new ThirdPartyException(ErrorCode.WECHAT_LOGIN_FAILED);
        }
    }

    private String friendlyWechatMessage(int errcode) {
        if (errcode == 40029 || errcode == 40163) {
            return "登录凭证已过期，请重新授权";
        }
        return ErrorCode.WECHAT_LOGIN_FAILED.getMessage();
    }
}
