package com.tracci.common.response;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ErrorCodeUniquenessTest {

    @Test
    void businessCodesAreUnique() {
        Map<Integer, List<ErrorCode>> byCode = Arrays.stream(ErrorCode.values())
                .collect(Collectors.groupingBy(ErrorCode::getCode));
        List<String> duplicates = byCode.entrySet().stream()
                .filter(entry -> entry.getValue().size() > 1)
                .map(entry -> entry.getKey() + " -> " + entry.getValue())
                .toList();
        assertEquals(List.of(), duplicates);
    }
}
