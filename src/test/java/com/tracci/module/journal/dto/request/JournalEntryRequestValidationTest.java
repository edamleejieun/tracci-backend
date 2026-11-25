package com.tracci.module.journal.dto.request;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JournalEntryRequestValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    void createReq_rejectsMissingOccurredAt() {
        JournalEntryCreateReq req = new JournalEntryCreateReq(
                "日志内容",
                null,
                null,
                null,
                null,
                false,
                null);

        assertFalse(validator.validate(req).isEmpty());
    }

    @Test
    void createReq_rejectsLatitudeOutOfRange() {
        JournalEntryCreateReq req = validCreateReq(
                BigDecimal.valueOf(90.0000001),
                null);

        assertFalse(validator.validate(req).isEmpty());
    }

    @Test
    void createReq_rejectsLongitudeOutOfRange() {
        JournalEntryCreateReq req = validCreateReq(
                null,
                BigDecimal.valueOf(-180.0000001));

        assertFalse(validator.validate(req).isEmpty());
    }

    @Test
    void createReq_acceptsLatitudeAndLongitudeBoundaries() {
        JournalEntryCreateReq req = validCreateReq(
                BigDecimal.valueOf(-90),
                BigDecimal.valueOf(180));

        assertTrue(validator.validate(req).isEmpty());
    }

    @Test
    void createReq_rejectsMoreThan9FileIds() {
        JournalEntryCreateReq req = new JournalEntryCreateReq(
                "日志",
                null,
                List.of("1", "2", "3", "4", "5", "6", "7", "8", "9", "10"),
                LocalDateTime.now(),
                null,
                false,
                null);
        assertFalse(validator.validate(req).isEmpty());
    }

    @Test
    void createReq_acceptsValidFileIds() {
        JournalEntryCreateReq req = new JournalEntryCreateReq(
                "日志",
                null,
                List.of("file-1", "file-2"),
                LocalDateTime.now(),
                null,
                false,
                null);
        assertTrue(validator.validate(req).isEmpty());
    }

    private JournalEntryCreateReq validCreateReq(BigDecimal latitude, BigDecimal longitude) {
        return new JournalEntryCreateReq(
                "日志内容",
                new JournalEntryLocationReq(null, "浅草寺", null, latitude, longitude, null, null, null, null, null, null, null, null),
                List.of("file-1"),
                LocalDateTime.of(2026, 7, 1, 10, 30),
                null,
                false,
                null);
    }
}
