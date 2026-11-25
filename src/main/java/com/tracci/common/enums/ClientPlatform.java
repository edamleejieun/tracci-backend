package com.tracci.common.enums;

import org.springframework.util.StringUtils;

public enum ClientPlatform {
    WECHAT_MINI,
    IOS,
    H5,
    UNKNOWN;

    public static ClientPlatform from(String value) {
        if (!StringUtils.hasText(value)) {
            return UNKNOWN;
        }
        String normalized = value.trim().toUpperCase();
        for (ClientPlatform platform : values()) {
            if (platform.name().equals(normalized)) {
                return platform;
            }
        }
        return UNKNOWN;
    }
}
