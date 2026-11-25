package com.tracci.module.trip.support;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ItineraryWriteContextTest {

    @AfterEach
    void tearDown() {
        // 防止用例之间串味
        ItineraryWriteContext.runAs(ItineraryWriteContext.MANUAL, () -> { });
    }

    @Test
    void defaultsToManual() {
        assertEquals(ItineraryWriteContext.MANUAL, ItineraryWriteContext.currentSource());
    }

    @Test
    void marksAssistantWritesAndRestoresAfterwards() {
        AtomicReference<String> inside = new AtomicReference<>();

        ItineraryWriteContext.runAs(ItineraryWriteContext.ASSISTANT,
                () -> inside.set(ItineraryWriteContext.currentSource()));

        assertEquals(ItineraryWriteContext.ASSISTANT, inside.get());
        assertEquals(ItineraryWriteContext.MANUAL, ItineraryWriteContext.currentSource());
    }

    @Test
    void restoresTheOuterSourceWhenNested() {
        AtomicReference<String> nested = new AtomicReference<>();

        ItineraryWriteContext.runAs(ItineraryWriteContext.MANUAL, () ->
                ItineraryWriteContext.runAs(ItineraryWriteContext.ASSISTANT,
                        () -> nested.set(ItineraryWriteContext.currentSource())));

        assertEquals(ItineraryWriteContext.ASSISTANT, nested.get());
        assertEquals(ItineraryWriteContext.MANUAL, ItineraryWriteContext.currentSource());
    }
}
