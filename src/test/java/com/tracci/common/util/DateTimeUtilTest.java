package com.tracci.common.util;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DateTimeUtilTest {

    @Test
    void toBusinessDate_convertsUtcWallClockToAsiaShanghaiDay() {
        // Shanghai midnight of 2026-07-09 is 2026-07-08T16:00:00Z
        LocalDateTime shanghaiMidnightAsUtc = LocalDateTime.of(2026, 7, 8, 16, 0, 0);
        assertEquals(LocalDate.of(2026, 7, 9), DateTimeUtil.toBusinessDate(shanghaiMidnightAsUtc));

        // Still morning in Shanghai on 2026-07-09 (07:30 CST = 23:30 previous day UTC)
        LocalDateTime earlyShanghaiMorning = LocalDateTime.of(2026, 7, 8, 23, 30, 0);
        assertEquals(LocalDate.of(2026, 7, 9), DateTimeUtil.toBusinessDate(earlyShanghaiMorning));

        // Afternoon UTC that stays on the same calendar date in Shanghai
        LocalDateTime sameUtcAndShanghaiDay = LocalDateTime.of(2026, 7, 9, 10, 0, 0);
        assertEquals(LocalDate.of(2026, 7, 9), DateTimeUtil.toBusinessDate(sameUtcAndShanghaiDay));
    }

    @Test
    void toBusinessDate_acceptsCustomZone() {
        LocalDateTime utc = LocalDateTime.of(2026, 7, 8, 16, 0, 0);
        assertEquals(LocalDate.of(2026, 7, 8), DateTimeUtil.toBusinessDate(utc, ZoneId.of("UTC")));
        assertEquals(LocalDate.of(2026, 7, 9), DateTimeUtil.toBusinessDate(utc, ZoneId.of("Asia/Tokyo")));
    }

    @Test
    void toBusinessDate_nullSafe() {
        assertNull(DateTimeUtil.toBusinessDate(null));
        assertNull(DateTimeUtil.toBusinessDate(null, ZoneId.of("UTC")));
    }

    @Test
    void toInstantString_treatsLocalDateTimeAsUtc() {
        LocalDateTime utc = LocalDateTime.of(2026, 7, 8, 16, 0, 0);
        assertEquals("2026-07-08T16:00:00Z", DateTimeUtil.toInstantString(utc));
    }

    @Test
    void todayBusiness_usesAsiaShanghaiZone() {
        assertEquals(LocalDate.now(DateTimeUtil.BUSINESS_ZONE), DateTimeUtil.todayBusiness());
    }
}
