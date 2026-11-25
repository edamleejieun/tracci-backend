package com.tracci.module.demo.dto.response;

import com.tracci.common.response.PageResponse;
import com.tracci.module.expense.dto.response.ExpenseResp;
import com.tracci.module.expense.dto.response.ExpenseSettlementResp;
import com.tracci.module.expense.dto.response.ExpenseSummaryResp;
import com.tracci.module.export.dto.response.ExportAssetsResp;
import com.tracci.module.export.dto.response.ItineraryExportAssetsResp;
import com.tracci.module.journal.dto.response.JournalEntryResp;
import com.tracci.module.summary.dto.response.SummaryResp;
import com.tracci.module.trip.dto.response.ItineraryResp;
import com.tracci.module.trip.dto.response.TripActivityLogResp;
import com.tracci.module.trip.dto.response.TripDetailResp;
import com.tracci.module.trip.dto.response.TripMemberResp;
import com.tracci.module.trip.dto.response.TripWeatherResponse;
import com.tracci.module.tripoverview.dto.response.TripStageOverviewResp;

import java.util.List;

public record DemoJourneySceneResp(
        TripDetailResp trip,
        ItineraryResp itinerary,
        PageResponse<JournalEntryResp> journals,
        PageResponse<ExpenseResp> expenses,
        ExpenseSummaryResp expenseSummary,
        ExpenseSettlementResp expenseSettlement,
        List<TripMemberResp> members,
        PageResponse<TripActivityLogResp> activityLogs,
        TripWeatherResponse weather,
        TripStageOverviewResp stageOverview,
        SummaryResp summary,
        DemoJourneyStatsResp stats,
        ExportAssetsResp exportAssets,
        ItineraryExportAssetsResp itineraryExportAssets
) {
}
