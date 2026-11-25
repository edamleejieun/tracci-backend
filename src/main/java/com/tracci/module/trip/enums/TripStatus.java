package com.tracci.module.trip.enums;

import java.time.LocalDate;
import java.util.Arrays;

public enum TripStatus {

    PLANNING("planning"),
    ONGOING("ongoing"),
    COMPLETED("completed");

    private final String value;

    TripStatus(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static TripStatus fromValue(String value) {
        return Arrays.stream(values())
                .filter(status -> status.value.equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("不支持的旅行状态"));
    }

    public static TripStatus calculate(LocalDate today, LocalDate startDate, LocalDate endDate) {
        if (today.isBefore(startDate)) {
            return PLANNING;
        }
        if (today.isAfter(endDate)) {
            return COMPLETED;
        }
        return ONGOING;
    }

    /**
     * 旅程是否已结束（today &gt; endDate）。start/end 任一为空时视为未结束，避免误锁。
     */
    public static boolean isCompleted(LocalDate today, LocalDate startDate, LocalDate endDate) {
        if (today == null || startDate == null || endDate == null) {
            return false;
        }
        return calculate(today, startDate, endDate) == COMPLETED;
    }

    public static String resolveValue(LocalDate today, LocalDate startDate, LocalDate endDate, String fallbackStatus) {
        if (today != null && startDate != null && endDate != null) {
            return calculate(today, startDate, endDate).value();
        }
        return fallbackStatus;
    }
}
