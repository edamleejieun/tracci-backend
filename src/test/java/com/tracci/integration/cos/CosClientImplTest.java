package com.tracci.integration.cos;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class CosClientImplTest {

    @Test
    void generatePostUploadCredential_signsOriginalPolicyJson() throws Exception {
        CosProperties properties = new CosProperties();
        properties.setSecretId("AKIDtest");
        properties.setSecretKey("secret-key");
        properties.setRegion("ap-shanghai");
        properties.setBucket("tracci-1311215926");

        CosClientImpl client = new CosClientImpl(properties);
        CosPostUploadCredential credential = client.generatePostUploadCredential(
                "users/u-1/journal_photo/2026/05/photo.jpg",
                "image/jpeg",
                5_242_880L,
                600);

        String encodedPolicy = credential.formData().get("policy");
        String keyTime = credential.formData().get("q-key-time");
        assertNotNull(encodedPolicy);
        assertEquals(keyTime, credential.formData().get("q-sign-time"));

        String policy = new String(Base64.getDecoder().decode(encodedPolicy), StandardCharsets.UTF_8);
        String signKey = hmacSha1Hex("secret-key", keyTime);
        String stringToSign = sha1Hex(policy);
        String expectedSignature = hmacSha1Hex(signKey, stringToSign);

        assertEquals(expectedSignature, credential.formData().get("q-signature"));
    }

    private String sha1Hex(String value) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-1");
        return toHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
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
