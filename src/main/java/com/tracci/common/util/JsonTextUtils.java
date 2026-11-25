package com.tracci.common.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

public final class JsonTextUtils {

    private static final Logger log = LoggerFactory.getLogger(JsonTextUtils.class);

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private static final TypeReference<List<Map<String, Object>>> LIST_MAP_TYPE = new TypeReference<>() {
    };

    private JsonTextUtils() {
    }

    public static Map<String, Object> parseMap(ObjectMapper mapper, String json) {
        if (!StringUtils.hasText(json)) {
            return Map.of();
        }
        try {
            return mapper.readValue(json, MAP_TYPE);
        } catch (Exception ex) {
            log.debug("Parse JSON object failed: {}", ex.getMessage());
            return Map.of();
        }
    }

    public static List<Map<String, Object>> listMap(ObjectMapper mapper, Object value) {
        if (value == null) {
            return List.of();
        }
        try {
            return mapper.convertValue(value, LIST_MAP_TYPE);
        } catch (Exception ex) {
            return List.of();
        }
    }

    public static String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    public static boolean containsAny(String text, String... candidates) {
        for (String candidate : candidates) {
            if (text.contains(candidate)) {
                return true;
            }
        }
        return false;
    }

    public static <T> List<T> safeList(List<T> list) {
        return list == null ? List.of() : list;
    }
}
