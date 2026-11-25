package com.tracci.module.journal.enums;

import java.util.Arrays;

public enum JournalMood {

    HAPPY("happy"),
    RELAXED("relaxed"),
    EXCITED("excited"),
    PEACEFUL("peaceful"),
    THOUGHTFUL("thoughtful");

    private final String value;

    JournalMood(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static JournalMood fromValue(String value) {
        return Arrays.stream(values())
                .filter(m -> m.value.equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unsupported journal mood: " + value));
    }
}
