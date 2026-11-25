package com.tracci.module.trip.dto.response;

import java.util.List;

public record ItineraryResp(
        String tripId,
        List<ItineraryDayResp> days
) {
}
