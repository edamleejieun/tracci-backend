package com.tracci.module.feedback.dto.request;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FeedbackRequestValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    void createReq_rejectsMissingType() {
        FeedbackCreateReq req = validReq(null, "content");
        assertFalse(validator.validate(req).isEmpty());
    }

    @Test
    void createReq_rejectsBlankContent() {
        FeedbackCreateReq req = validReq("BUG", "   ");
        assertFalse(validator.validate(req).isEmpty());
    }

    @Test
    void createReq_rejectsContentOver1000Chars() {
        FeedbackCreateReq req = validReq("BUG", "x".repeat(1001));
        assertFalse(validator.validate(req).isEmpty());
    }

    @Test
    void createReq_acceptsShortContent() {
        FeedbackCreateReq req = validReq("BUG", "闪退");
        assertTrue(validator.validate(req).isEmpty());
    }

    @Test
    void createReq_rejectsMoreThan3ImageIds() {
        FeedbackCreateReq req = new FeedbackCreateReq(
                "BUG",
                null,
                "反馈内容",
                null,
                null,
                List.of("1", "2", "3", "4"),
                "WECHAT_MINI",
                "1.0.0",
                null);
        assertFalse(validator.validate(req).isEmpty());
    }

    @Test
    void createReq_acceptsEmptySystemInfo() {
        FeedbackCreateReq req = validReq("BUG", "反馈内容");
        assertTrue(validator.validate(req).isEmpty());
    }

    private static FeedbackCreateReq validReq(String type, String content) {
        return new FeedbackCreateReq(
                type,
                "标题",
                content,
                "WECHAT",
                "contact-id",
                List.of("file-1"),
                "WECHAT_MINI",
                "1.0.0",
                Map.of("brand", "iPhone"));
    }
}
