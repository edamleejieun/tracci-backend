package com.tracci.module.stats.service;

import com.tracci.module.stats.dto.response.StatsOverviewResp;
import com.tracci.module.stats.dto.response.StatsFootprintAnalysisResp;
import com.tracci.module.stats.dto.response.StatsRecordsAnalysisResp;
import com.tracci.module.stats.dto.response.StatsSpendAnalysisResp;

public interface StatsService {

    default StatsOverviewResp getOverview(String tripId) {
        return getOverview(tripId, null);
    }

    StatsOverviewResp getOverview(String tripId, String timeZone);

    default StatsSpendAnalysisResp getSpendAnalysis(String tripId) {
        return getSpendAnalysis(tripId, null);
    }

    StatsSpendAnalysisResp getSpendAnalysis(String tripId, String timeZone);

    default StatsRecordsAnalysisResp getRecordsAnalysis(String tripId) {
        return getRecordsAnalysis(tripId, null);
    }

    StatsRecordsAnalysisResp getRecordsAnalysis(String tripId, String timeZone);

    default StatsFootprintAnalysisResp getFootprintAnalysis(String tripId) {
        return getFootprintAnalysis(tripId, null);
    }

    StatsFootprintAnalysisResp getFootprintAnalysis(String tripId, String timeZone);
}
