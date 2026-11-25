package com.tracci.module.stats.dto.response;

import java.util.List;

public record StatsSpendAnalysisResp(
        String totalAmount,
        String averageDailyExpense,
        String averageTripExpense,
        long expenseCount,
        String currency,
        String topCategory,
        String topCategoryName,
        String insight,
        List<StatsOverviewResp.CategoryStat> categoryStats,
        List<TripSpendEfficiency> tripEfficiency
) {

    public record TripSpendEfficiency(
            String tripId,
            String title,
            String destinationName,
            long totalDays,
            String totalAmount,
            String averageDailyExpense
    ) {
    }
}
