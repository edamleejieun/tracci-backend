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
 * 调用微信开放平台 OAuth2 access_token 与 sns/userinfo
 */
@Slf4j
public class WechatAppClientImpl implements WechatAppClient {

    private final RestClient restClient;
    private final WechatProperties.App properties;
    private final ObjectMapper objectMapper;

    public WechatAppClientImpl(
            RestClient.Builder restClientBuilder,
            WechatProperties.App properties,
            ObjectMapper objectMapper) {
        this.restClient = restClientBuilder.build();
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public WechatOAuthResult exchangeCode(String code) {
        if (!StringUtils.hasText(properties.getAppId()) || !StringUtils.hasText(properties.getAppSecret())) {
            log.error("Wechat app config missing appIdPresent={} appSecretPresent={}",
                    StringUtils.hasText(properties.getAppId()), StringUtils.hasText(properties.getAppSecret()));
            throw new BizException(ErrorCode.WECHAT_CONFIG_MISSING);
        }
        try {
            JsonNode tokenRoot = fetchAccessToken(code);
            if (tokenRoot.hasNonNull("errcode") && tokenRoot.get("errcode").asInt() != 0) {
                int errcode = tokenRoot.path("errcode").asInt();
                String errmsg = tokenRoot.path("errmsg").asText("wechat error");
                log.warn("Wechat app oauth access_token returned errcode={} errmsg={}", errcode, errmsg);
                throw new ThirdPartyException(ErrorCode.WECHAT_LOGIN_FAILED, friendlyWechatMessage(errcode));
            }
            String openid = tokenRoot.path("openid").asText(null);
            if (!StringUtils.hasText(openid)) {
                log.warn("Wechat app oauth access_token response missing openid");
                throw new ThirdPartyException(ErrorCode.WECHAT_LOGIN_FAILED);
            }
            String accessToken = tokenRoot.path("access_token").asText(null);
            String unionid = textOrNull(tokenRoot, "unionid");

            String nickname = null;
            String avatarUrl = null;
            if (!StringUtils.hasText(unionid) && StringUtils.hasText(accessToken)) {
                JsonNode userInfo = fetchUserInfo(accessToken, openid);
                if (userInfo != null) {
                    unionid = textOrNull(userInfo, "unionid");
                    nickname = textOrNull(userInfo, "nickname");
                    avatarUrl = textOrNull(userInfo, "headimgurl");
                }
            }
            return new WechatOAuthResult(openid, unionid, nickname, avatarUrl);
        } catch (ThirdPartyException ex) {
            throw ex;
        } catch (BizException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("Wechat app oauth request failed message={}", ex.getMessage());
            throw new ThirdPartyException(ErrorCode.WECHAT_SERVICE_UNAVAILABLE);
        }
    }

    private JsonNode fetchAccessToken(String code) throws Exception {
        URI endpoint = URI.create(properties.getAccessTokenEndpoint());
        String raw = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .scheme(endpoint.getScheme())
                        .host(endpoint.getHost())
                        .path(endpoint.getPath())
                        .queryParam("appid", properties.getAppId())
                        .queryParam("secret", properties.getAppSecret())
                        .queryParam("code", code)
                        .queryParam("grant_type", "authorization_code")
                        .build())
                .retrieve()
                .body(String.class);
        return objectMapper.readTree(raw);
    }

    private JsonNode fetchUserInfo(String accessToken, String openid) {
        try {
            URI endpoint = URI.create(properties.getUserInfoEndpoint());
            String raw = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .scheme(endpoint.getScheme())
                            .host(endpoint.getHost())
                            .path(endpoint.getPath())
                            .queryParam("access_token", accessToken)
                            .queryParam("openid", openid)
                            .queryParam("lang", "zh_CN")
                            .build())
                    .retrieve()
                    .body(String.class);
            JsonNode root = objectMapper.readTree(raw);
            if (root.hasNonNull("errcode") && root.get("errcode").asInt() != 0) {
                int errcode = root.path("errcode").asInt();
                String errmsg = root.path("errmsg").asText("wechat error");
                log.warn("Wechat app userinfo returned errcode={} errmsg={}", errcode, errmsg);
                return null;
            }
            return root;
        } catch (Exception ex) {
            log.warn("Wechat app userinfo request failed message={}", ex.getMessage());
            return null;
        }
    }

    private String textOrNull(JsonNode node, String field) {
        String value = node.path(field).asText(null);
        return StringUtils.hasText(value) ? value : null;
    }

    private String friendlyWechatMessage(int errcode) {
        if (errcode == 40029 || errcode == 40163) {
            return "登录凭证已过期，请重新授权";
        }
        return ErrorCode.WECHAT_LOGIN_FAILED.getMessage();
    }
}
