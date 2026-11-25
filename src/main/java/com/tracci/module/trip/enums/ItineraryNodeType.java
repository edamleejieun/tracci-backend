package com.tracci.module.trip.enums;

import java.util.Arrays;

public enum ItineraryNodeType {

    ATTRACTION("attraction"),
    RESTAURANT("restaurant"),
    TRANSPORT("transport"),
    HOTEL("hotel"),
    SHOPPING("shopping"),
    OTHER("other");

    private final String value;

    ItineraryNodeType(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static ItineraryNodeType fromValue(String value) {
        return Arrays.stream(values())
                .filter(type -> type.value.equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported itinerary node type: " + value));
    }
}
