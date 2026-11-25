package com.tracci.module.trip.dto.response;

public record TripInvitationJoinResp(
        String tripId,
        String memberId,
        String role,
        String status,
        String joinedAt
) {
}
