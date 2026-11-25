package com.tracci.integration.cos;

import com.tracci.module.file.entity.File;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class FileUrlResolver {

    private final CosClient cosClient;
    private final CosProperties cosProperties;
    private final CiUrlBuilder ciUrlBuilder;

    public FileUrlResolver(CosClient cosClient, CosProperties cosProperties, CiUrlBuilder ciUrlBuilder) {
        this.cosClient = cosClient;
        this.cosProperties = cosProperties;
        this.ciUrlBuilder = ciUrlBuilder;
    }

    /**
     * Resolve the access URL for a file.
     * Returns the public CDN URL if configured, otherwise generates a presigned GET URL.
     * Returns null if the file is null, not UPLOADED, or has no objectKey.
     */
    public String resolve(File file) {
        if (file == null || !"UPLOADED".equals(file.getStatus()) || !StringUtils.hasText(file.getObjectKey())) {
            return null;
        }
        if (StringUtils.hasText(file.getUrl())) {
            return file.getUrl();
        }
        return cosClient.generatePresignedAccessUrl(file.getObjectKey(), cosProperties.getAccessUrlExpireSeconds());
    }

    public CiUrlBuilder.CiUrls resolveUrls(File file) {
        if (!isResolvable(file)) {
            return CiUrlBuilder.CiUrls.empty();
        }
        if (StringUtils.hasText(file.getUrl())) {
            return ciUrlBuilder.build(file.getUrl(), file.getContentType());
        }
        int expireSeconds = cosProperties.getAccessUrlExpireSeconds();
        String originalUrl = cosClient.generatePresignedAccessUrl(file.getObjectKey(), expireSeconds);
        return ciUrlBuilder.buildSigned(
                originalUrl,
                file.getContentType(),
                rule -> cosClient.generatePresignedAccessUrl(file.getObjectKey(), expireSeconds, rule));
    }

    private boolean isResolvable(File file) {
        return file != null && "UPLOADED".equals(file.getStatus()) && StringUtils.hasText(file.getObjectKey());
    }
}
