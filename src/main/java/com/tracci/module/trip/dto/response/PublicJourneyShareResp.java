package com.tracci.module.trip.dto.response;

public record PublicJourneyShareResp(
        String tripId,
        String title,
        String startDate,
        String endDate,
        long totalDays,
        TripDestinationResp destination,
        TripCoverPhotoResp coverPhoto,
        long placeCount,
        long itineraryCount,
        long journalCount,
        long memberCount,
        String shareUrl,
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
