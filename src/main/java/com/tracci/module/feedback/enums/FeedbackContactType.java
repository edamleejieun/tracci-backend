package com.tracci.module.feedback.enums;

import com.tracci.common.exception.BizException;
import com.tracci.common.response.ErrorCode;

import java.util.Arrays;

public enum FeedbackContactType {

    WECHAT("WECHAT", "微信号"),
    EMAIL("EMAIL", "邮箱"),
    PHONE("PHONE", "手机号"),
    OTHER("OTHER", "其他");

    private final String value;
    private final String label;

    FeedbackContactType(String value, String label) {
        this.value = value;
        this.label = label;
    }

    public String value() {
        return value;
    }

    public String label() {
        return label;
    }

    public static FeedbackContactType fromValue(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toUpperCase();
        return Arrays.stream(values())
                .filter(type -> type.value.equals(normalized))
                .findFirst()
                .orElseThrow(() -> new BizException(ErrorCode.AUTH_PARAM_INVALID, "联系方式类型不合法"));
    }
}
