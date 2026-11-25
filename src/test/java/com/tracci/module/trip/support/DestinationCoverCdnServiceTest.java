package com.tracci.module.trip.support;

import com.tracci.integration.cos.CiUrlBuilder;
import com.tracci.integration.cos.CiProperties;
import com.tracci.integration.cos.CosClient;
import com.tracci.integration.cos.CosProperties;
import com.tracci.integration.cos.MockCosClient;
import com.tracci.module.trip.config.TripProperties;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DestinationCoverCdnServiceTest {

    @Test
    void presetCover_buildsUrlsFromConfiguredPublicBaseUrl() {
        DestinationCoverCdnService service = new DestinationCoverCdnService(
                new TripProperties(),
                enabledCosProperties(),
                new MockCosClient("test-bucket", "ap-guangzhou"),
                new CiUrlBuilder(new CiProperties()));

        Optional<CachedDestinationCover> cover = service.presetCover("chongqing");

        assertTrue(cover.isPresent());
        assertEquals(
                "https://cdn.example.com/system/destination-covers/v1/cities/chongqing.jpg",
                cover.get().url());
        assertEquals("system/destination-covers/v1/cities/chongqing.jpg", cover.get().objectKey());
        assertTrue(cover.get().thumbnailUrl().contains("imageMogr2/thumbnail/720x"));
    }

    @Test
    void objectKeyForCity_usesConfiguredPrefix() {
        TripProperties props = new TripProperties();
        props.getDestinationCover().setCitiesObjectKeyPrefix("system/destination-covers/v1/cities/");
        DestinationCoverCdnService service = new DestinationCoverCdnService(
                props,
                enabledCosProperties(),
                new MockCosClient("test-bucket", "ap-guangzhou"),
                new CiUrlBuilder(new CiProperties()));

        assertEquals(
                "system/destination-covers/v1/cities/shanghai.jpg",
                service.objectKeyForCity("shanghai"));
    }

    @Test
    void fallbackCover_buildsUrlsFromConfiguredPublicBaseUrl() {
        DestinationCoverCdnService service = new DestinationCoverCdnService(
                new TripProperties(),
                enabledCosProperties(),
                new MockCosClient("test-bucket", "ap-guangzhou"),
                new CiUrlBuilder(new CiProperties()));

        Optional<CachedDestinationCover> cover = service.fallbackCover();

        assertTrue(cover.isPresent());
        assertEquals(
                "https://cdn.example.com/system/destination-covers/default.jpg",
                cover.get().url());
        assertEquals(1600, cover.get().width());
        assertEquals(1000, cover.get().height());
        assertTrue(cover.get().thumbnailUrl().contains("imageMogr2/thumbnail/720x"));
        assertEquals("system/destination-covers/default.jpg", cover.get().objectKey());
    }

    @Test
    void fallbackCover_usesPresignedUrlWhenPublicBaseUrlMissing() {
        CosProperties cosProperties = enabledCosProperties();
        cosProperties.setPublicBaseUrl(null);

        DestinationCoverCdnService service = new DestinationCoverCdnService(
                new TripProperties(),
                cosProperties,
                new MockCosClient("tracci-prod", "ap-guangzhou"),
                new CiUrlBuilder(new CiProperties()));

        Optional<CachedDestinationCover> cover = service.fallbackCover();

        assertTrue(cover.isPresent());
        assertEquals(
                "https://tracci-prod.cos.ap-guangzhou.myqcloud.com/system/destination-covers/default.jpg"
                        + "?mock-access-signature=true&expires=1800",
                cover.get().url());
    }

    @Test
    void fallbackCover_seedsBundledAssetWithoutExternalNetwork() {
        CosClient cosClient = mock(CosClient.class);
        when(cosClient.getObjectBytes("system/destination-covers/default.jpg"))
                .thenThrow(new IllegalStateException("missing"));

        DestinationCoverCdnService service = new DestinationCoverCdnService(
                new TripProperties(),
                enabledCosProperties(),
                cosClient,
                new CiUrlBuilder(new CiProperties()));

        Optional<CachedDestinationCover> cover = service.fallbackCover();

        assertTrue(cover.isPresent());
        verify(cosClient).putObject(
                eq("system/destination-covers/default.jpg"),
                argThat(bytes -> bytes.length > 1_000
                        && (bytes[0] & 0xFF) == 0xFF
                        && (bytes[1] & 0xFF) == 0xD8
                        && (bytes[2] & 0xFF) == 0xFF),
                eq("image/jpeg"));
    }

    private CosProperties enabledCosProperties() {
        CosProperties cosProperties = new CosProperties();
        cosProperties.setEnabled(true);
        cosProperties.setBucket("test-bucket");
        cosProperties.setRegion("ap-guangzhou");
        cosProperties.setPublicBaseUrl("https://cdn.example.com");
        return cosProperties;
    }
}
