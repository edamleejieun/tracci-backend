package com.tracci.module.trip.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tracci.module.trip.config.TripProperties;
import com.tracci.module.trip.entity.Trip;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TripDestinationCoverProviderTest {

    @Mock
    private DestinationCoverCdnService destinationCoverCdnService;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;

    private TripDestinationCoverProvider provider;

    @BeforeEach
    void setUp() {
        TripProperties tripProperties = new TripProperties();
        ObjectMapper objectMapper = new ObjectMapper();
        DestinationCoverCityResolver cityResolver = new DestinationCoverCityResolver();

        provider = new TripDestinationCoverProvider(
                tripProperties,
                cityResolver,
                destinationCoverCdnService,
                redisTemplate,
                objectMapper);

        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void resolveTripCover_usesPresetCityObject() {
        Trip trip = new Trip();
        trip.setDestinationCityName("重庆");

        when(valueOperations.get("trip:dest-cover:preset:v2:chongqing")).thenReturn(null);
        when(destinationCoverCdnService.presetCover("chongqing"))
                .thenReturn(Optional.of(new CachedDestinationCover(
                        "https://cdn.example.com/system/destination-covers/v1/cities/chongqing.jpg",
                        "https://cdn.example.com/system/destination-covers/v1/cities/chongqing.jpg?thumb",
                        "https://cdn.example.com/system/destination-covers/v1/cities/chongqing.jpg?preview",
                        "https://cdn.example.com/system/destination-covers/v1/cities/chongqing.jpg?export",
                        "https://cdn.example.com/system/destination-covers/v1/cities/chongqing.jpg?portrait",
                        "https://cdn.example.com/system/destination-covers/v1/cities/chongqing.jpg?card",
                        "https://cdn.example.com/system/destination-covers/v1/cities/chongqing.jpg?square",
                        1600,
                        1000,
                        "system/destination-covers/v1/cities/chongqing.jpg",
                        "image/jpeg")));

        var cover = provider.resolveTripCover(trip);

        assertTrue(cover.isPresent());
        assertEquals(
                "https://cdn.example.com/system/destination-covers/v1/cities/chongqing.jpg",
                cover.get().url());
        verify(destinationCoverCdnService).presetCover("chongqing");
        verify(destinationCoverCdnService, never()).fallbackCover();
    }

    @Test
    void resolveTripCover_fallsBackWhenCityUnknown() {
        Trip trip = new Trip();
        trip.setDestinationName("某个小镇");

        when(valueOperations.get("trip:dest-cover:preset:v2:__fallback__")).thenReturn(null);
        when(destinationCoverCdnService.fallbackCover()).thenReturn(Optional.of(new CachedDestinationCover(
                "https://cdn.example.com/system/destination-covers/default.jpg",
                "https://cdn.example.com/system/destination-covers/default.jpg?thumb",
                "https://cdn.example.com/system/destination-covers/default.jpg?preview",
                "https://cdn.example.com/system/destination-covers/default.jpg?export",
                "https://cdn.example.com/system/destination-covers/default.jpg?portrait",
                "https://cdn.example.com/system/destination-covers/default.jpg?card",
                "https://cdn.example.com/system/destination-covers/default.jpg?square",
                1600,
                1000,
                "system/destination-covers/default.jpg",
                "image/jpeg")));

        var cover = provider.resolveTripCover(trip);

        assertTrue(cover.isPresent());
        assertEquals("https://cdn.example.com/system/destination-covers/default.jpg", cover.get().url());
        verify(destinationCoverCdnService, never()).presetCover(anyString());
        verify(destinationCoverCdnService).fallbackCover();
    }

    @Test
    void resolveTripCover_fallsBackWhenPresetMissing() {
        Trip trip = new Trip();
        trip.setDestinationCityName("上海");

        when(valueOperations.get("trip:dest-cover:preset:v2:shanghai")).thenReturn(null);
        when(valueOperations.get("trip:dest-cover:preset:v2:__fallback__")).thenReturn(null);
        when(destinationCoverCdnService.presetCover("shanghai")).thenReturn(Optional.empty());
        when(destinationCoverCdnService.fallbackCover()).thenReturn(Optional.of(new CachedDestinationCover(
                "https://cdn.example.com/system/destination-covers/default.jpg",
                null,
                null,
                null,
                null,
                null,
                null,
                1600,
                1000,
                "system/destination-covers/default.jpg",
                "image/jpeg")));

        var cover = provider.resolveTripCover(trip);

        assertTrue(cover.isPresent());
        assertEquals("https://cdn.example.com/system/destination-covers/default.jpg", cover.get().url());
        verify(destinationCoverCdnService).presetCover("shanghai");
        verify(destinationCoverCdnService).fallbackCover();
    }

    @Test
    void resolveTripCover_returnsEmptyWhenNoDestination() {
        assertTrue(provider.resolveTripCover(new Trip()).isEmpty());
        verifyNoInteractions(destinationCoverCdnService);
    }
}
