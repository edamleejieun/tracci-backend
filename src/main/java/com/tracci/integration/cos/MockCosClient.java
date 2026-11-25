package com.tracci.integration.cos;

import java.util.Map;

public class MockCosClient implements CosClient {

    private final String bucket;
    private final String region;

    public MockCosClient(String bucket, String region) {
        this.bucket = bucket;
        this.region = region;
    }

    @Override
    public String generatePresignedUploadUrl(String objectKey, String contentType, int expireSeconds) {
        return "https://" + bucket + ".cos." + region + ".myqcloud.com/"
                + objectKey + "?mock-upload-signature=true&expires=" + expireSeconds;
    }

    @Override
    public CosPostUploadCredential generatePostUploadCredential(
            String objectKey,
            String contentType,
            long maxSizeBytes,
            int expireSeconds) {
        return new CosPostUploadCredential(
                "https://" + bucket + ".cos." + region + ".myqcloud.com",
                Map.of(
                        "key", objectKey,
                        "policy", "mock-policy",
                        "q-sign-algorithm", "sha1",
                        "q-ak", "mock-secret-id",
                        "q-sign-time", "0;" + expireSeconds,
                        "q-key-time", "0;" + expireSeconds,
                        "q-signature", "mock-post-signature",
                        "Content-Type", contentType,
                        "success_action_status", "200"
                ),
                "file");
    }

    @Override
    public String generatePresignedAccessUrl(String objectKey, int expireSeconds) {
        return "https://" + bucket + ".cos." + region + ".myqcloud.com/"
                + objectKey + "?mock-access-signature=true&expires=" + expireSeconds;
    }

    @Override
    public String generatePresignedAccessUrl(String objectKey, int expireSeconds, String ciRule) {
        String encodedCiRule = ciRule
                .replace("&", "%26")
                .replace("=", "%3D")
                .replace(">", "%3E")
                .replace(" ", "%20");
        return "https://" + bucket + ".cos." + region + ".myqcloud.com/"
                + objectKey + "?" + encodedCiRule
                + "&mock-access-signature=true&expires=" + expireSeconds;
    }

    @Override
    public void putObject(String objectKey, byte[] content, String contentType) {
        // no-op in mock mode
    }

    @Override
    public byte[] getObjectBytes(String objectKey) {
        return ("mock-object:" + objectKey).getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    @Override
    public boolean objectExists(String objectKey) {
        return true;
    }

    @Override
    public Map<String, String> generateUploadHeaders(String contentType) {
        return Map.of("Content-Type", contentType);
    }
}
