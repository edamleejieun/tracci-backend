package com.tracci.module.trip.dto.response;

public record TripShareResp(
        String tripId,
        Boolean shareEnabled,
        String shareUrl
) {
}
