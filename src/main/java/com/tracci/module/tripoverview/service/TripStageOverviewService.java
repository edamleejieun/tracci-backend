package com.tracci.module.tripoverview.service;

import com.tracci.module.tripoverview.dto.response.TripStageOverviewResp;

public interface TripStageOverviewService {

    TripStageOverviewResp getOverview(String tripId, String clientPlatform, String timeZone);
}
