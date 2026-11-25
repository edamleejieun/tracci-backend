package com.tracci.module.trip.support;

import com.tracci.module.trip.entity.Trip;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DestinationCoverCityResolverTest {

    private final DestinationCoverCityResolver resolver = new DestinationCoverCityResolver();

    @Test
    void resolveCityKey_fromChineseCityName() {
        Trip trip = new Trip();
        trip.setDestinationCityName("重庆");

        assertEquals("chongqing", resolver.resolveCityKey(trip).orElseThrow());
    }

    @Test
    void resolveCityKey_stripsShiSuffix() {
        Trip trip = new Trip();
        trip.setDestinationCityName("重庆市");

        assertEquals("chongqing", resolver.resolveCityKey(trip).orElseThrow());
    }

    @Test
    void resolveCityKey_fallsBackToDestinationName() {
        Trip trip = new Trip();
        trip.setDestinationName("杭州");

        assertEquals("hangzhou", resolver.resolveCityKey(trip).orElseThrow());
    }

    @Test
    void resolveCityKey_emptyForForeignCity() {
        Trip trip = new Trip();
        trip.setDestinationCityName("东京");

        assertTrue(resolver.resolveCityKey(trip).isEmpty());
    }

    @Test
    void resolveCityKey_acceptsAsciiKey() {
        Trip trip = new Trip();
        trip.setDestinationCityName("Chongqing");

        assertEquals("chongqing", resolver.resolveCityKey(trip).orElseThrow());
    }
}
