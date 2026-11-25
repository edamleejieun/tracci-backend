package com.tracci.module.demo.dto.response;

import com.tracci.module.stats.dto.response.StatsFootprintAnalysisResp;
import com.tracci.module.stats.dto.response.StatsOverviewResp;
import com.tracci.module.stats.dto.response.StatsRecordsAnalysisResp;
import com.tracci.module.stats.dto.response.StatsSpendAnalysisResp;

public record DemoJourneyStatsResp(
        StatsOverviewResp overview,
        StatsSpendAnalysisResp spendAnalysis,
        StatsRecordsAnalysisResp recordsAnalysis,
        StatsFootprintAnalysisResp footprintAnalysis
) {
}
