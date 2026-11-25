package com.tracci.module.feedback.enums;

import java.util.Arrays;

public enum FeedbackStatus {

    SUBMITTED("SUBMITTED", "已提交"),
    PROCESSING("PROCESSING", "处理中"),
    RESOLVED("RESOLVED", "已解决"),
    CLOSED("CLOSED", "已关闭");

    private final String value;
    private final String label;

    FeedbackStatus(String value, String label) {
        this.value = value;
        this.label = label;
    }

    public String value() {
        return value;
    }

    public String label() {
        return label;
    }

    public static FeedbackStatus fromValue(String value) {
        if (value == null || value.isBlank()) {
            return SUBMITTED;
        }
        String normalized = value.trim().toUpperCase();
        return Arrays.stream(values())
                .filter(status -> status.value.equals(normalized))
                .findFirst()
                .orElse(SUBMITTED);
    }
}
