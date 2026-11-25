package com.tracci.module.trip.dto.response;

import java.util.List;

public record TripActivityLogResp(
        String id,
        String tripId,
        Actor actor,
        String action,
        String targetType,
        String targetId,
        String summary,
        List<FieldChange> fieldChanges,
        String createdAt
) {

    public record Actor(String userId, String memberId, String nickname) {
    }

    public record FieldChange(String field, String oldValue, String newValue) {
    }
}
