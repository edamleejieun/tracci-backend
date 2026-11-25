package com.tracci.integration.cos;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CiUrlBuilderTest {

    private CiProperties ciProps(boolean enabled) {
        CiProperties props = new CiProperties();
        props.setEnabled(enabled);
        props.setThumbnailRule("imageMogr2/thumbnail/720x/quality/85");
        props.setPreviewRule("imageView2/2/w/1080/q/85");
        props.setExportRule("imageMogr2/thumbnail/1080x>/quality/90");
        props.setStoryPortraitRule("imageMogr2/scrop/1080x1920/format/webp/quality/88");
        props.setStoryCardRule("imageMogr2/scrop/1080x1440/format/webp/quality/88");
        props.setSquareRule("imageMogr2/scrop/720x720/format/webp/quality/85");
        props.setWatermarkEnabled(false);
        props.setWatermarkRule("");
        return props;
    }

    @Test
    void build_generatesThumbnailUrl() {
        CiUrlBuilder builder = new CiUrlBuilder(ciProps(true));
        var urls = builder.build("https://cdn.example.com/photo.jpg", "image/jpeg");

        assertEquals("https://cdn.example.com/photo.jpg", urls.url());
        assertEquals("https://cdn.example.com/photo.jpg?imageMogr2/thumbnail/720x/quality/85", urls.thumbnailUrl());
        assertEquals("https://cdn.example.com/photo.jpg?imageView2/2/w/1080/q/85", urls.previewUrl());
        assertEquals("https://cdn.example.com/photo.jpg?imageMogr2/thumbnail/1080x>/quality/90", urls.exportUrl());
        assertEquals("https://cdn.example.com/photo.jpg?imageMogr2/scrop/1080x1920/format/webp/quality/88", urls.storyPortraitUrl());
        assertEquals("https://cdn.example.com/photo.jpg?imageMogr2/scrop/1080x1440/format/webp/quality/88", urls.storyCardUrl());
        assertEquals("https://cdn.example.com/photo.jpg?imageMogr2/scrop/720x720/format/webp/quality/85", urls.squareUrl());
        assertNull(urls.watermarkedUrl());
    }

    @Test
    void build_appendsWithAmpersandWhenUrlHasQueryParams() {
        CiUrlBuilder builder = new CiUrlBuilder(ciProps(true));
        var urls = builder.build("https://cdn.example.com/photo.jpg?sign=abc123", "image/png");

        assertEquals("https://cdn.example.com/photo.jpg?sign=abc123&imageMogr2/thumbnail/720x/quality/85", urls.thumbnailUrl());
    }

    @Test
    void build_fallbackWhenDisabled() {
        CiUrlBuilder builder = new CiUrlBuilder(ciProps(false));
        var urls = builder.build("https://cdn.example.com/photo.jpg", "image/jpeg");

        assertEquals("https://cdn.example.com/photo.jpg", urls.url());
        assertEquals("https://cdn.example.com/photo.jpg", urls.thumbnailUrl());
        assertEquals("https://cdn.example.com/photo.jpg", urls.previewUrl());
        assertEquals("https://cdn.example.com/photo.jpg", urls.exportUrl());
        assertEquals("https://cdn.example.com/photo.jpg", urls.storyPortraitUrl());
        assertEquals("https://cdn.example.com/photo.jpg", urls.storyCardUrl());
        assertEquals("https://cdn.example.com/photo.jpg", urls.squareUrl());
        assertNull(urls.watermarkedUrl());
    }

    @Test
    void build_nonImageReturnsOnlyUrl() {
        CiUrlBuilder builder = new CiUrlBuilder(ciProps(true));
        var urls = builder.build("https://cdn.example.com/file.pdf", "application/pdf");

        assertEquals("https://cdn.example.com/file.pdf", urls.url());
        assertNull(urls.thumbnailUrl());
        assertNull(urls.previewUrl());
        assertNull(urls.exportUrl());
        assertNull(urls.storyPortraitUrl());
        assertNull(urls.storyCardUrl());
        assertNull(urls.squareUrl());
        assertNull(urls.watermarkedUrl());
    }

    @Test
    void build_nullContentTypeTreatedAsNonImage() {
        CiUrlBuilder builder = new CiUrlBuilder(ciProps(true));
        var urls = builder.build("https://cdn.example.com/file.bin", null);

        assertEquals("https://cdn.example.com/file.bin", urls.url());
        assertNull(urls.thumbnailUrl());
    }

    @Test
    void build_emptyBaseUrlReturnsEmpty() {
        CiUrlBuilder builder = new CiUrlBuilder(ciProps(true));
        var urls = builder.build("", "image/jpeg");

        assertNull(urls.url());
        assertNull(urls.thumbnailUrl());
        assertNull(urls.previewUrl());
        assertNull(urls.exportUrl());
        assertNull(urls.storyPortraitUrl());
        assertNull(urls.storyCardUrl());
        assertNull(urls.squareUrl());
        assertNull(urls.watermarkedUrl());
    }

    @Test
    void build_nullBaseUrlReturnsEmpty() {
        CiUrlBuilder builder = new CiUrlBuilder(ciProps(true));
        var urls = builder.build(null, "image/jpeg");

        assertNull(urls.url());
        assertNull(urls.thumbnailUrl());
        assertNull(urls.previewUrl());
        assertNull(urls.exportUrl());
        assertNull(urls.storyPortraitUrl());
        assertNull(urls.storyCardUrl());
        assertNull(urls.squareUrl());
        assertNull(urls.watermarkedUrl());
    }

    @Test
    void build_generatesWatermarkUrlWhenEnabled() {
        CiProperties props = ciProps(true);
        props.setWatermarkEnabled(true);
        props.setWatermarkRule("watermark/1/image/aHR0cHM6Ly9sb2dvLnBuZw==");
        CiUrlBuilder builder = new CiUrlBuilder(props);

        var urls = builder.build("https://cdn.example.com/photo.jpg", "image/jpeg");

        assertEquals("https://cdn.example.com/photo.jpg?watermark/1/image/aHR0cHM6Ly9sb2dvLnBuZw==", urls.watermarkedUrl());
        assertEquals("https://cdn.example.com/photo.jpg?imageMogr2/thumbnail/720x/quality/85", urls.thumbnailUrl());
    }

    @Test
    void build_watermarkedUrlNullWhenDisabled() {
        CiUrlBuilder builder = new CiUrlBuilder(ciProps(true));
        var urls = builder.build("https://cdn.example.com/photo.jpg", "image/jpeg");

        assertNull(urls.watermarkedUrl());
    }

    @Test
    void build_watermarkedUrlNullWhenRuleEmpty() {
        CiProperties props = ciProps(true);
        props.setWatermarkEnabled(true);
        props.setWatermarkRule("");
        CiUrlBuilder builder = new CiUrlBuilder(props);

        var urls = builder.build("https://cdn.example.com/photo.jpg", "image/jpeg");

        assertNull(urls.watermarkedUrl());
    }

    @Test
    void buildDerivedUrl_returnsBaseUrlWhenRuleIsNull() {
        CiProperties props = ciProps(true);
        props.setThumbnailRule(null);
        CiUrlBuilder builder = new CiUrlBuilder(props);

        var urls = builder.build("https://cdn.example.com/photo.jpg", "image/jpeg");

        assertEquals("https://cdn.example.com/photo.jpg", urls.thumbnailUrl());
    }
}
