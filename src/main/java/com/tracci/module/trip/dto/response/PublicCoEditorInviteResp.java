package com.tracci.module.trip.dto.response;

public record PublicCoEditorInviteResp(
        String tripId,
        String inviteCode,
        boolean valid,
        String status,
        String roleToGrant,
        String expiresAt,
        PublicJourneyShareResp trip,
        Meta meta
) {
    public record Meta(
            String title,
            String description,
            String imageUrl,
            String url
    ) {
    }
}
