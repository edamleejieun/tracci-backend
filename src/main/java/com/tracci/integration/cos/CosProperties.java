package com.tracci.integration.cos;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Getter
@Setter
@Component
@ConfigurationProperties("tracci.integration.cos")
public class CosProperties {

    private boolean enabled;
    private boolean mock;
    private String secretId;
    private String secretKey;
    private String region;
    private String bucket;
    private String publicBaseUrl;
    private int uploadUrlExpireSeconds = 600;
    private int accessUrlExpireSeconds = 1800;
    private long maxImageSizeBytes = 5_242_880L;
    private List<String> allowedImageTypes = List.of(
            "image/jpeg",
            "image/png",
            "image/webp",
            "image/avif",
            "image/gif",
            "image/bmp",
            "image/tiff",
            "image/heic",
            "image/heif");
}
