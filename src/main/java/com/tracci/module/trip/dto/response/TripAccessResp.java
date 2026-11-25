package com.tracci.module.trip.dto.response;

import com.tracci.module.trip.service.TripAccessService;

import java.util.List;

public record TripAccessResp(
    String role,
    List<String> permissions
) {
    public static TripAccessResp from(TripAccessService.TripAccess access) {
        if (access == null || access.role() == null) {
            return null;
        }
        return new TripAccessResp(
                access.role().value(),
                access.permissions().stream()
                        .map(permission -> permission.value())
                        .sorted()
                        .toList());
    }
}
