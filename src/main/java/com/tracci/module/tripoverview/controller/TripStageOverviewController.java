package com.tracci.module.tripoverview.controller;

import com.tracci.common.response.ApiResponse;
import com.tracci.module.tripoverview.dto.response.TripStageOverviewResp;
import com.tracci.module.tripoverview.service.TripStageOverviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/trips/stage-overview")
@RequiredArgsConstructor
public class TripStageOverviewController {

    private final TripStageOverviewService tripStageOverviewService;

    @GetMapping
    public ApiResponse<TripStageOverviewResp> overview(
            @RequestParam(required = false) String tripId,
            @RequestParam(required = false) String clientPlatform,
            @RequestParam(required = false) String timeZone) {
        return ApiResponse.ok(tripStageOverviewService.getOverview(tripId, clientPlatform, timeZone));
    }
}
