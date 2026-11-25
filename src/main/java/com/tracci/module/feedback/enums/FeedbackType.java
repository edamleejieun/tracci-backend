package com.tracci.module.feedback.enums;

import com.tracci.common.exception.BizException;
import com.tracci.common.response.ErrorCode;

import java.util.Arrays;

public enum FeedbackType {

    BUG("BUG", "Bug 反馈"),
    FEATURE("FEATURE", "功能建议"),
    UX("UX", "体验问题"),
    ACCOUNT("ACCOUNT", "账号问题"),
    OTHER("OTHER", "其他");

    private final String value;
    private final String label;

    FeedbackType(String value, String label) {
        this.value = value;
        this.label = label;
    }

    public String value() {
        return value;
    }

    public String label() {
        return label;
    }

    public static FeedbackType fromValue(String value) {
        if (value == null || value.isBlank()) {
            throw new BizException(ErrorCode.AUTH_PARAM_INVALID, "反馈类型不能为空");
        }
        String normalized = value.trim().toUpperCase();
        return Arrays.stream(values())
                .filter(type -> type.value.equals(normalized))
                .findFirst()
                .orElseThrow(() -> new BizException(ErrorCode.AUTH_PARAM_INVALID, "反馈类型不合法"));
    }
}
