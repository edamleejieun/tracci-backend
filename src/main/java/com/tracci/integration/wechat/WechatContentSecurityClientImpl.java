package com.tracci.integration.wechat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tracci.common.exception.BizException;
import com.tracci.common.exception.ThirdPartyException;
import com.tracci.common.response.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Slf4j
public class WechatContentSecurityClientImpl implements WechatContentSecurityClient {

    private static final int TOKEN_REFRESH_SAFETY_SECONDS = 120;

    private final RestClient restClient;
    private final WechatProperties.MiniProgram properties;
    private final ObjectMapper objectMapper;

    private volatile String cachedAccessToken;
    private volatile Instant cachedAccessTokenExpiresAt = Instant.EPOCH;

    public WechatContentSecurityClientImpl(
            RestClient.Builder restClientBuilder,
            WechatProperties.MiniProgram properties,
            ObjectMapper objectMapper) {
        this.restClient = restClientBuilder.build();
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public void checkText(String openid, String content) {
        if (!StringUtils.hasText(content)) {
            return;
        }
        if (!StringUtils.hasText(openid)) {
            log.warn("Wechat content security skipped because openid is missing");
            throw new BizException(ErrorCode.CONTENT_SECURITY_UNAVAILABLE);
        }
        checkText(openid, content, false);
    }

    @Override
    public void checkImage(String openid, byte[] imageBytes) {
        if (imageBytes == null || imageBytes.length == 0) {
            return;
        }
        if (!StringUtils.hasText(openid)) {
            log.warn("Wechat image security skipped because openid is missing");
            throw new BizException(ErrorCode.CONTENT_SECURITY_UNAVAILABLE);
        }
        checkImage(openid, imageBytes, false);
    }

    private void checkText(String openid, String content, boolean retried) {
        try {
            String accessToken = getAccessToken();
            JsonNode root = postMsgSecCheck(accessToken, openid, content);
            int errcode = root.path("errcode").asInt(0);
            if (errcode == 0) {
                String suggest = root.path("result").path("suggest").asText("pass");
                if ("pass".equalsIgnoreCase(suggest)) {
                    return;
                }
                log.info("Wechat msgSecCheck rejected content suggest={} label={}",
                        suggest, root.path("result").path("label").asText(""));
                throw new BizException(ErrorCode.CONTENT_SECURITY_REJECTED);
            }
            if (isAccessTokenExpired(errcode) && !retried) {
                invalidateAccessToken();
                checkText(openid, content, true);
                return;
            }
            log.warn("Wechat msgSecCheck returned errcode={} errmsg={}",
                    errcode, root.path("errmsg").asText(""));
            throw new ThirdPartyException(ErrorCode.CONTENT_SECURITY_UNAVAILABLE);
        } catch (BizException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("Wechat msgSecCheck request failed message={}", ex.getMessage());
            throw new ThirdPartyException(ErrorCode.CONTENT_SECURITY_UNAVAILABLE);
        }
    }

    private JsonNode postMsgSecCheck(String accessToken, String openid, String content) throws Exception {
        URI endpoint = URI.create(properties.getMsgSecCheckEndpoint());
        String raw = restClient.post()
                .uri(uriBuilder -> uriBuilder
                        .scheme(endpoint.getScheme())
                        .host(endpoint.getHost())
                        .path(endpoint.getPath())
                        .queryParam("access_token", accessToken)
                        .build())
                .body(Map.of(
                        "content", content,
                        "version", 2,
                        "scene", 1,
                        "openid", openid))
                .retrieve()
                .body(String.class);
        return objectMapper.readTree(raw);
    }

    private void checkImage(String openid, byte[] imageBytes, boolean retried) {
        try {
            String accessToken = getAccessToken();
            WechatSecCheckImagePreparer.PreparedImage preparedImage = WechatSecCheckImagePreparer.prepare(imageBytes);
            JsonNode root = postImgSecCheck(accessToken, preparedImage);
            int errcode = root.path("errcode").asInt(0);
            if (errcode == 0) {
                return;
            }
            if (errcode == 87014) {
                log.info("Wechat imgSecCheck rejected image openid={}", openid);
                throw new BizException(ErrorCode.CONTENT_SECURITY_REJECTED);
            }
            if (isAccessTokenExpired(errcode) && !retried) {
                invalidateAccessToken();
                checkImage(openid, imageBytes, true);
                return;
            }
            log.warn("Wechat imgSecCheck returned errcode={} errmsg={}",
                    errcode, root.path("errmsg").asText(""));
            throw new ThirdPartyException(ErrorCode.CONTENT_SECURITY_UNAVAILABLE);
        } catch (BizException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("Wechat imgSecCheck request failed message={}", ex.getMessage());
            throw new ThirdPartyException(ErrorCode.CONTENT_SECURITY_UNAVAILABLE);
        }
    }

    private JsonNode postImgSecCheck(
            String accessToken,
            WechatSecCheckImagePreparer.PreparedImage preparedImage) throws Exception {
        URI endpoint = URI.create(properties.getImgSecCheckEndpoint());
        String boundary = "----TracciWechatBoundary" + UUID.randomUUID().toString().replace("-", "");
        byte[] body = buildImageMultipartBody(boundary, preparedImage);

        String raw = restClient.post()
                .uri(uriBuilder -> uriBuilder
                        .scheme(endpoint.getScheme())
                        .host(endpoint.getHost())
                        .path(endpoint.getPath())
                        .queryParam("access_token", accessToken)
                        .build())
                .contentType(MediaType.parseMediaType("multipart/form-data; boundary=" + boundary))
                .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(body.length))
                .body(body)
                .retrieve()
                .body(String.class);
        return objectMapper.readTree(raw);
    }

    private byte[] buildImageMultipartBody(
            String boundary,
            WechatSecCheckImagePreparer.PreparedImage preparedImage) {
        String header = "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"media\"; filename=\""
                + preparedImage.filename() + "\"\r\n"
                + "Content-Type: " + preparedImage.mediaType() + "\r\n\r\n";
        String footer = "\r\n--" + boundary + "--\r\n";
        byte[] headerBytes = header.getBytes(StandardCharsets.UTF_8);
        byte[] footerBytes = footer.getBytes(StandardCharsets.UTF_8);
        byte[] imageBytes = preparedImage.bytes();
        byte[] body = new byte[headerBytes.length + imageBytes.length + footerBytes.length];
        System.arraycopy(headerBytes, 0, body, 0, headerBytes.length);
        System.arraycopy(imageBytes, 0, body, headerBytes.length, imageBytes.length);
        System.arraycopy(footerBytes, 0, body, headerBytes.length + imageBytes.length, footerBytes.length);
        return body;
    }

    private String getAccessToken() throws Exception {
        Instant now = Instant.now();
        String token = cachedAccessToken;
        if (StringUtils.hasText(token) && now.isBefore(cachedAccessTokenExpiresAt)) {
            return token;
        }
        synchronized (this) {
            token = cachedAccessToken;
            if (StringUtils.hasText(token) && now.isBefore(cachedAccessTokenExpiresAt)) {
                return token;
            }
            return fetchAccessToken(now);
        }
    }

    private String fetchAccessToken(Instant now) throws Exception {
        if (!StringUtils.hasText(properties.getAppId()) || !StringUtils.hasText(properties.getAppSecret())) {
            log.error("Wechat mini program config missing for content security appIdPresent={} appSecretPresent={}",
                    StringUtils.hasText(properties.getAppId()), StringUtils.hasText(properties.getAppSecret()));
            throw new ThirdPartyException(ErrorCode.CONTENT_SECURITY_UNAVAILABLE);
        }
        URI endpoint = URI.create(properties.getAccessTokenEndpoint());
        String raw = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .scheme(endpoint.getScheme())
                        .host(endpoint.getHost())
                        .path(endpoint.getPath())
                        .queryParam("grant_type", "client_credential")
                        .queryParam("appid", properties.getAppId())
                        .queryParam("secret", properties.getAppSecret())
                        .build())
                .retrieve()
                .body(String.class);
        JsonNode root = objectMapper.readTree(raw);
        if (root.hasNonNull("errcode") && root.path("errcode").asInt() != 0) {
            log.warn("Wechat access token request returned errcode={} errmsg={}",
                    root.path("errcode").asInt(), root.path("errmsg").asText(""));
            throw new ThirdPartyException(ErrorCode.CONTENT_SECURITY_UNAVAILABLE);
        }
        String token = root.path("access_token").asText(null);
        if (!StringUtils.hasText(token)) {
            log.warn("Wechat access token response missing access_token");
            throw new ThirdPartyException(ErrorCode.CONTENT_SECURITY_UNAVAILABLE);
        }
        long expiresIn = Math.max(root.path("expires_in").asLong(7200) - TOKEN_REFRESH_SAFETY_SECONDS, 60);
        cachedAccessToken = token;
        cachedAccessTokenExpiresAt = now.plusSeconds(expiresIn);
        return token;
    }

    private void invalidateAccessToken() {
        cachedAccessToken = null;
        cachedAccessTokenExpiresAt = Instant.EPOCH;
    }

    private boolean isAccessTokenExpired(int errcode) {
        return errcode == 40001 || errcode == 42001;
    }
}
