package com.tracci.module.trip.dto.response;

public record TripInvitationCreateResp(
        String id,
        String tripId,
        String inviteCode,
        String roleToGrant,
        String expiresAt,
        Integer maxUses,
        Integer usedCount,
        String webInviteUrl,
        String miniProgramPath,
        String appDeepLink,
        String shareText
) {
}
