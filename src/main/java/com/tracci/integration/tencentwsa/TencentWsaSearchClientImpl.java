package com.tracci.integration.tencentwsa;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;

@Slf4j
class TencentWsaSearchClientImpl implements TencentWsaSearchClient {

    private static final String SIGN_ALGORITHM = "TC3-HMAC-SHA256";
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String CONTENT_TYPE = "application/json; charset=utf-8";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final TencentWsaProperties properties;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    TencentWsaSearchClientImpl(TencentWsaProperties properties, RestClient restClient, ObjectMapper objectMapper) {
        this.properties = properties;
        this.restClient = restClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public JsonNode search(String query, Integer limit) {
        if (!properties.isEnabled()) {
            throw new IllegalStateException("Tencent WSA search is disabled");
        }
        if (!StringUtils.hasText(properties.getSecretId()) || !StringUtils.hasText(properties.getSecretKey())) {
            throw new IllegalStateException("Tencent WSA secret-id/secret-key is not configured");
        }
        if (!StringUtils.hasText(query)) {
            throw new IllegalArgumentException("query is required");
        }
        int cnt = normalizeLimit(limit);
        var body = objectMapper.createObjectNode()
                .put("Query", query)
                .put("Cnt", cnt);
        if (properties.getMode() != null) {
            body.put("Mode", properties.getMode());
        }
        String payload = body.toString();
        long timestamp = Instant.now().getEpochSecond();
        URI endpoint = URI.create(properties.getEndpoint());
        String authorization = authorization(endpoint, payload, timestamp);
        try {
            JsonNode response = restClient.post()
                    .uri(endpoint)
                    .header("Authorization", authorization)
                    .header("Content-Type", CONTENT_TYPE)
                    .header("Host", endpoint.getHost())
                    .header("X-TC-Action", properties.getAction())
                    .header("X-TC-Version", properties.getVersion())
                    .header("X-TC-Timestamp", String.valueOf(timestamp))
                    .body(payload)
                    .retrieve()
                    .body(JsonNode.class);
            validateTencentResponse(response);
            return response;
        } catch (Exception e) {
            log.warn("Tencent WSA search failed queryLength={} message={}", query.length(), e.getMessage());
            throw e;
        }
    }

    private String authorization(URI endpoint, String payload, long timestamp) {
        String host = endpoint.getHost();
        String canonicalHeaders = "content-type:" + CONTENT_TYPE + "\n"
                + "host:" + host + "\n"
                + "x-tc-action:" + properties.getAction().toLowerCase() + "\n";
        String signedHeaders = "content-type;host;x-tc-action";
        String canonicalRequest = "POST\n"
                + "/\n"
                + "\n"
                + canonicalHeaders
                + "\n"
                + signedHeaders
                + "\n"
                + sha256Hex(payload);
        String date = LocalDateTime.ofEpochSecond(timestamp, 0, ZoneOffset.UTC).format(DATE_FORMATTER);
        String credentialScope = date + "/" + properties.getService() + "/tc3_request";
        String stringToSign = SIGN_ALGORITHM + "\n"
                + timestamp + "\n"
                + credentialScope + "\n"
                + sha256Hex(canonicalRequest);
        byte[] secretDate = hmacSha256(("TC3" + properties.getSecretKey()).getBytes(StandardCharsets.UTF_8), date);
        byte[] secretService = hmacSha256(secretDate, properties.getService());
        byte[] secretSigning = hmacSha256(secretService, "tc3_request");
        String signature = HexFormat.of().formatHex(hmacSha256(secretSigning, stringToSign));
        return SIGN_ALGORITHM
                + " Credential=" + properties.getSecretId() + "/" + credentialScope
                + ", SignedHeaders=" + signedHeaders
                + ", Signature=" + signature;
    }

    private void validateTencentResponse(JsonNode response) {
        if (response == null || response.isNull()) {
            throw new IllegalStateException("Tencent WSA returned empty response");
        }
        JsonNode error = response.path("Response").path("Error");
        if (error.isObject()) {
            String code = error.path("Code").asText("UNKNOWN");
            String message = error.path("Message").asText("");
            throw new IllegalStateException("Tencent WSA error " + code + ": " + message);
        }
        JsonNode pages = response.path("Response").path("Pages");
        if (!pages.isArray()) {
            log.debug("Tencent WSA response has no Response.Pages; topFields={}, responseFields={}",
                    response.fieldNames(), response.path("Response").fieldNames());
        }
    }

    private String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private byte[] hmacSha256(byte[] key, String value) {
        try {
            return hmacSha256(key, value.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("HMAC-SHA256 failed", e);
        }
    }

    private byte[] hmacSha256(byte[] key, byte[] value) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(key, HMAC_ALGORITHM));
            return mac.doFinal(value);
        } catch (Exception e) {
            throw new IllegalStateException("HMAC-SHA256 unavailable", e);
        }
    }

    private int normalizeLimit(Integer limit) {
        int value = limit == null || limit <= 0 ? properties.getDefaultLimit() : limit;
        if (value <= 10) {
            return 10;
        }
        if (value <= 20) {
            return 20;
        }
        if (value <= 30) {
            return 30;
        }
        if (value <= 40) {
            return 40;
        }
        return 50;
    }
}
