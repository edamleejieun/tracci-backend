package com.tracci.module.stats.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record StatsOverviewResp(
        Scope scope,
        Overview overview,
        ExpenseStats expenseStats,
        List<RecentTrip> recentTrips,
        List<MonthlyTrendItem> monthlyTrend,
        List<DailyTrendItem> dailyTrend,
        List<InsightItem> insights,
        List<AnalysisEntry> analysisEntries,
        MemoryFacts memory
) {

    public record Scope(
            String type,
            String tripId,
            String tripTitle
    ) {
    }

    public record Overview(
            long tripCount,
            long totalTravelDays,
            long itineraryCount,
            long journalCount,
            long photoCount,
            long destinationCount,
            long expenseCount,
            String totalExpenseAmount,
            String averageDailyExpense,
            String currency
    ) {
    }

    public record ExpenseStats(
            String totalAmount,
            String currency,
            List<CategoryStat> categoryStats
    ) {
    }

    public record CategoryStat(
            String category,
            String categoryName,
            String amount,
            String percentage,
            long count
    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record RecentTrip(
            String id,
            String title,
            String destinationName,
            String startDate,
            String endDate,
            String status,
            long totalDays,
            String totalExpenseAmount,
            String averageDailyExpense
    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record MonthlyTrendItem(
            String yearMonth,
            String amount,
            long count
    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record DailyTrendItem(
            String date,
            int dayIndex,
            String highlight,
            Long itineraryCount,
            Long journalCount,
            String expenseAmount,
            Long photoCount
    ) {
    }

    public record InsightItem(
            String type,
            String text,
            String relatedTripId
    ) {
    }

    public record AnalysisEntry(
            String kind,
            String title,
            String subtitle
    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record MemoryFacts(
            long journalCount,
            long photoCount,
            Long userFeaturedJournalCount,
            Long userFeaturedPhotoCount,
            Long systemRecommendedPhotoCount,
            Boolean coverAvailable
    ) {
    }
}
