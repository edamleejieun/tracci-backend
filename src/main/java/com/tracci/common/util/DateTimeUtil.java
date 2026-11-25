package com.tracci.common.util;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Instant-style timestamps are stored as {@link LocalDateTime} in UTC wall-clock
 * (see {@link #nowUtc()} / {@link #toInstantString(LocalDateTime)}). Business-day
 * grouping for trip journals must convert through that UTC instant into the
 * product business zone ({@link #BUSINESS_ZONE}), matching iOS {@code TracciDateContext}.
 */
public final class DateTimeUtil {

    /** Product business day zone — aligned with iOS {@code TracciDateContext.timeZone}. */
    public static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    private static final DateTimeFormatter INSTANT_FMT = DateTimeFormatter.ISO_INSTANT;
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    private DateTimeUtil() {}

    public static LocalDateTime nowUtc() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }

    /**
     * Product business calendar date in {@link #BUSINESS_ZONE} (Asia/Shanghai).
     * Use this for trip status (planning/ongoing/completed) so server and iOS
     * {@code TracciDateContext.today()} share the same day boundary.
     */
    public static LocalDate todayBusiness() {
        return LocalDate.now(BUSINESS_ZONE);
    }

    public static String toInstantString(LocalDateTime time) {
        if (time == null) return null;
        return INSTANT_FMT.format(time.atOffset(ZoneOffset.UTC).toInstant());
    }

    public static String toTimeString(LocalTime time) {
        if (time == null) return null;
        return TIME_FMT.format(time);
    }

    /**
     * Convert a UTC-stored {@link LocalDateTime} to the product business calendar date
     * ({@link #BUSINESS_ZONE}).
     */
    public static LocalDate toBusinessDate(LocalDateTime utcDateTime) {
        return toBusinessDate(utcDateTime, BUSINESS_ZONE);
    }

    /**
     * Convert a UTC-stored {@link LocalDateTime} to a calendar date in {@code zoneId}.
     * Null input returns null; null zone falls back to {@link #BUSINESS_ZONE}.
     */
    public static LocalDate toBusinessDate(LocalDateTime utcDateTime, ZoneId zoneId) {
        if (utcDateTime == null) {
            return null;
        }
        ZoneId zone = zoneId != null ? zoneId : BUSINESS_ZONE;
        return utcDateTime.atOffset(ZoneOffset.UTC).atZoneSameInstant(zone).toLocalDate();
    }
}
