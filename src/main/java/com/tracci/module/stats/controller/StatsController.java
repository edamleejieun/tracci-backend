package com.tracci.module.stats.controller;

import com.tracci.common.response.ApiResponse;
import com.tracci.module.stats.dto.response.StatsFootprintAnalysisResp;
import com.tracci.module.stats.dto.response.StatsOverviewResp;
import com.tracci.module.stats.dto.response.StatsRecordsAnalysisResp;
import com.tracci.module.stats.dto.response.StatsSpendAnalysisResp;
import com.tracci.module.stats.service.StatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/stats")
@RequiredArgsConstructor
public class StatsController {

    private final StatsService statsService;

    @GetMapping("/overview")
    public ApiResponse<StatsOverviewResp> overview(
            @RequestParam(required = false) String tripId,
            @RequestParam(required = false) String timeZone) {
        return ApiResponse.ok(statsService.getOverview(tripId, timeZone));
    }

    @GetMapping("/analysis/spend")
    public ApiResponse<StatsSpendAnalysisResp> spendAnalysis(
            @RequestParam(required = false) String tripId,
            @RequestParam(required = false) String timeZone) {
        return ApiResponse.ok(statsService.getSpendAnalysis(tripId, timeZone));
    }

    @GetMapping("/analysis/records")
    public ApiResponse<StatsRecordsAnalysisResp> recordsAnalysis(
            @RequestParam(required = false) String tripId,
            @RequestParam(required = false) String timeZone) {
        return ApiResponse.ok(statsService.getRecordsAnalysis(tripId, timeZone));
    }

    @GetMapping("/analysis/footprint")
    public ApiResponse<StatsFootprintAnalysisResp> footprintAnalysis(
            @RequestParam(required = false) String tripId,
            @RequestParam(required = false) String timeZone) {
        return ApiResponse.ok(statsService.getFootprintAnalysis(tripId, timeZone));
    }
}
