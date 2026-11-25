package com.tracci.module.trip.dto.response;

public record TripMemberResp(
        String id,
        String tripId,
        String userId,
        String nickname,
        String avatarUrl,
        String role,
        String status,
        String accountStatus,
        String joinedAt
) {
}
