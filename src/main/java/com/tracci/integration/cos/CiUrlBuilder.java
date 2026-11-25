package com.tracci.integration.cos;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.function.Function;

@Component
public class CiUrlBuilder {

    private final CiProperties ciProperties;

    public CiUrlBuilder(CiProperties ciProperties) {
        this.ciProperties = ciProperties;
    }

    public CiUrls build(String baseUrl, String contentType) {
        if (!StringUtils.hasText(baseUrl)) {
            return CiUrls.empty();
        }
        if (!ciProperties.isEnabled()) {
            return CiUrls.fallback(baseUrl);
        }
        if (!isImage(contentType)) {
            return CiUrls.nonImage(baseUrl);
        }
        return new CiUrls(
                baseUrl,
                buildDerivedUrl(baseUrl, ciProperties.getThumbnailRule()),
                buildDerivedUrl(baseUrl, ciProperties.getPreviewRule()),
                buildDerivedUrl(baseUrl, ciProperties.getExportRule()),
                buildDerivedUrl(baseUrl, ciProperties.getStoryPortraitRule()),
                buildDerivedUrl(baseUrl, ciProperties.getStoryCardRule()),
                buildDerivedUrl(baseUrl, ciProperties.getSquareRule()),
                buildWatermarkedUrl(baseUrl));
    }

    public CiUrls buildSigned(String originalUrl, String contentType, Function<String, String> signedUrlFactory) {
        if (!StringUtils.hasText(originalUrl)) {
            return CiUrls.empty();
        }
        if (!ciProperties.isEnabled()) {
            return CiUrls.fallback(originalUrl);
        }
        if (!isImage(contentType)) {
            return CiUrls.nonImage(originalUrl);
        }
        return new CiUrls(
                originalUrl,
                buildSignedDerivedUrl(ciProperties.getThumbnailRule(), originalUrl, signedUrlFactory),
                buildSignedDerivedUrl(ciProperties.getPreviewRule(), originalUrl, signedUrlFactory),
                buildSignedDerivedUrl(ciProperties.getExportRule(), originalUrl, signedUrlFactory),
                buildSignedDerivedUrl(ciProperties.getStoryPortraitRule(), originalUrl, signedUrlFactory),
                buildSignedDerivedUrl(ciProperties.getStoryCardRule(), originalUrl, signedUrlFactory),
                buildSignedDerivedUrl(ciProperties.getSquareRule(), originalUrl, signedUrlFactory),
                buildSignedWatermarkedUrl(originalUrl, signedUrlFactory));
    }

    private String buildWatermarkedUrl(String baseUrl) {
        if (!ciProperties.isWatermarkEnabled() || !StringUtils.hasText(ciProperties.getWatermarkRule())) {
            return null;
        }
        return buildDerivedUrl(baseUrl, ciProperties.getWatermarkRule());
    }

    private String buildSignedWatermarkedUrl(String originalUrl, Function<String, String> signedUrlFactory) {
        if (!ciProperties.isWatermarkEnabled() || !StringUtils.hasText(ciProperties.getWatermarkRule())) {
            return null;
        }
        return buildSignedDerivedUrl(ciProperties.getWatermarkRule(), originalUrl, signedUrlFactory);
    }

    private String buildSignedDerivedUrl(String rule, String originalUrl, Function<String, String> signedUrlFactory) {
        if (!StringUtils.hasText(rule)) {
            return originalUrl;
        }
        return signedUrlFactory.apply(rule);
    }

    String buildDerivedUrl(String baseUrl, String rule) {
        if (!StringUtils.hasText(rule)) {
            return baseUrl;
        }
        if (baseUrl.contains("?")) {
            return baseUrl + "&" + rule;
        }
        return baseUrl + "?" + rule;
    }

    private boolean isImage(String contentType) {
        return StringUtils.hasText(contentType) && contentType.startsWith("image/");
    }

    public record CiUrls(
            String url,
            String thumbnailUrl,
            String previewUrl,
            String exportUrl,
            String storyPortraitUrl,
            String storyCardUrl,
            String squareUrl,
            String watermarkedUrl
    ) {
        static CiUrls empty() {
            return new CiUrls(null, null, null, null, null, null, null, null);
        }

        static CiUrls fallback(String url) {
            return new CiUrls(url, url, url, url, url, url, url, null);
        }

        static CiUrls nonImage(String url) {
            return new CiUrls(url, null, null, null, null, null, null, null);
        }

        /** CI 关闭或测试 mock 时，所有衍生 URL 与原始 URL 相同。 */
        public static CiUrls mirror(String url) {
            return fallback(url);
        }
    }
}
