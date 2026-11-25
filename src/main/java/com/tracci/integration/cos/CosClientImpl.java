package com.tracci.integration.cos;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.auth.COSCredentials;
import com.qcloud.cos.http.HttpMethodName;
import com.qcloud.cos.http.HttpProtocol;
import com.qcloud.cos.model.COSObject;
import com.qcloud.cos.model.GeneratePresignedUrlRequest;
import com.qcloud.cos.model.ObjectMetadata;
import com.qcloud.cos.model.PutObjectRequest;
import com.qcloud.cos.region.Region;
import com.tracci.common.exception.ThirdPartyException;
import com.tracci.common.response.ErrorCode;

import java.io.ByteArrayInputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Date;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class CosClientImpl implements CosClient {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String POST_FILE_FIELD_NAME = "file";

    private final COSClient cosClient;
    private final String bucket;
    private final String region;
    private final String secretId;
    private final String secretKey;

    public CosClientImpl(CosProperties properties) {
        COSCredentials cred = new BasicCOSCredentials(properties.getSecretId(), properties.getSecretKey());
        ClientConfig clientConfig = new ClientConfig(new Region(properties.getRegion()));
        clientConfig.setHttpProtocol(HttpProtocol.https);
        this.cosClient = new COSClient(cred, clientConfig);
        this.bucket = properties.getBucket();
        this.region = properties.getRegion();
        this.secretId = properties.getSecretId();
        this.secretKey = properties.getSecretKey();
    }

    @Override
    public String generatePresignedUploadUrl(String objectKey, String contentType, int expireSeconds) {
        try {
            Date expiration = new Date(System.currentTimeMillis() + expireSeconds * 1000L);
            GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest(bucket, objectKey, HttpMethodName.PUT);
            request.setExpiration(expiration);
            request.setContentType(contentType);
            URL url = cosClient.generatePresignedUrl(request);
            return url.toString();
        } catch (Exception e) {
            throw new ThirdPartyException(ErrorCode.OSS_PRESIGN_FAILED);
        }
    }

    @Override
    public CosPostUploadCredential generatePostUploadCredential(
            String objectKey,
            String contentType,
            long maxSizeBytes,
            int expireSeconds) {
        try {
            long start = Instant.now().getEpochSecond();
            long end = start + expireSeconds;
            String keyTime = start + ";" + end;
            String policy = buildPostPolicy(objectKey, contentType, maxSizeBytes, keyTime, end);
            String encodedPolicy = Base64.getEncoder().encodeToString(policy.getBytes(StandardCharsets.UTF_8));
            String signKey = hmacSha1Hex(secretKey, keyTime);
            String stringToSign = sha1Hex(policy);
            String signature = hmacSha1Hex(signKey, stringToSign);

            Map<String, String> formData = new LinkedHashMap<>();
            formData.put("key", objectKey);
            formData.put("policy", encodedPolicy);
            formData.put("q-sign-algorithm", "sha1");
            formData.put("q-ak", secretId);
            formData.put("q-sign-time", keyTime);
            formData.put("q-key-time", keyTime);
            formData.put("q-signature", signature);
            formData.put("Content-Type", contentType);
            formData.put("success_action_status", "200");

            return new CosPostUploadCredential(
                    "https://" + bucket + ".cos." + region + ".myqcloud.com",
                    formData,
                    POST_FILE_FIELD_NAME);
        } catch (Exception e) {
            throw new ThirdPartyException(ErrorCode.OSS_PRESIGN_FAILED);
        }
    }

    @Override
    public String generatePresignedAccessUrl(String objectKey, int expireSeconds) {
        try {
            Date expiration = new Date(System.currentTimeMillis() + expireSeconds * 1000L);
            GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest(bucket, objectKey, HttpMethodName.GET);
            request.setExpiration(expiration);
            URL url = cosClient.generatePresignedUrl(request);
            return url.toString();
        } catch (Exception e) {
            throw new ThirdPartyException(ErrorCode.OSS_PRESIGN_FAILED);
        }
    }

    @Override
    public String generatePresignedAccessUrl(String objectKey, int expireSeconds, String ciRule) {
        try {
            Date expiration = new Date(System.currentTimeMillis() + expireSeconds * 1000L);
            GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest(bucket, objectKey, HttpMethodName.GET);
            request.setExpiration(expiration);
            request.addRequestParameter(ciRule, null);
            URL url = cosClient.generatePresignedUrl(request);
            return url.toString();
        } catch (Exception e) {
            throw new ThirdPartyException(ErrorCode.OSS_PRESIGN_FAILED);
        }
    }

    @Override
    public void putObject(String objectKey, byte[] content, String contentType) {
        try {
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(content.length);
            metadata.setContentType(contentType);
            PutObjectRequest request = new PutObjectRequest(
                    bucket,
                    objectKey,
                    new ByteArrayInputStream(content),
                    metadata);
            cosClient.putObject(request);
        } catch (Exception e) {
            throw new ThirdPartyException(ErrorCode.OSS_PRESIGN_FAILED);
        }
    }

    @Override
    public byte[] getObjectBytes(String objectKey) {
        try (COSObject object = cosClient.getObject(bucket, objectKey);
             var input = object.getObjectContent()) {
            return input.readAllBytes();
        } catch (Exception e) {
            throw new ThirdPartyException(ErrorCode.CONTENT_SECURITY_UNAVAILABLE);
        }
    }

    @Override
    public boolean objectExists(String objectKey) {
        try {
            return cosClient.doesObjectExist(bucket, objectKey);
        } catch (Exception e) {
            throw new ThirdPartyException(ErrorCode.OSS_PRESIGN_FAILED);
        }
    }

    @Override
    public Map<String, String> generateUploadHeaders(String contentType) {
        return Map.of("Content-Type", contentType);
    }

    private String buildPostPolicy(
            String objectKey,
            String contentType,
            long maxSizeBytes,
            String keyTime,
            long expireEpochSeconds) throws Exception {
        Map<String, Object> policy = new LinkedHashMap<>();
        policy.put("expiration", Instant.ofEpochSecond(expireEpochSeconds).toString());
        policy.put("conditions", List.of(
                Map.of("q-sign-algorithm", "sha1"),
                Map.of("q-ak", secretId),
                Map.of("q-sign-time", keyTime),
                Map.of("bucket", bucket),
                Map.of("key", objectKey),
                Map.of("Content-Type", contentType),
                Map.of("success_action_status", "200"),
                List.of("content-length-range", 1, maxSizeBytes)
        ));
        return OBJECT_MAPPER.writeValueAsString(policy);
    }

    private String sha1Hex(String value) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-1");
        byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
        return toHex(bytes);
    }

    private String hmacSha1Hex(String key, String value) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA1");
        mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
        return toHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
    }

    private String toHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            builder.append(String.format("%02x", b));
        }
        return builder.toString();
    }
}
