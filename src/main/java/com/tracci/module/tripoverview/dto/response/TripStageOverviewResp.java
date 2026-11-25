package com.tracci.module.tripoverview.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.tracci.module.summary.dto.response.SummaryResp;

import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TripStageOverviewResp(
        String generatedDate,
        String timeZone,
        boolean timeZoneFallbackApplied,
        String phase,
        FocusTrip focusTrip,
        Selection selection,
        Permissions permissions,
        PlanningInsight planning,
        OngoingInsight ongoing,
        MemoryMaterial memory,
        CompletedInsight completed,
        List<ActionItem> actions
) {
    public record FocusTrip(
            String id, String title, String destinationName, String startDate, String endDate, long totalDays) {
    }

    public record Selection(String mode, String reason, int candidateCount, List<CandidateTrip> candidates) {
    }

    public record CandidateTrip(
            String id, String title, String destinationName, String startDate, String endDate,
            long totalDays, String status, String coverSource) {
    }

    public record Permissions(boolean canAddJournal, boolean canFeaturePhoto) {
    }

    public record PlanningInsight(
            long totalDays,
            long scheduledDayCount,
            List<DailyNodeCount> dailyNodeCounts,
            Density density,
            long departureDays,
            WeatherAvailability weather,
            List<String> unplannedDates) {
    }

    public record DailyNodeCount(String date, int dayIndex, long nodeCount) {
    }

    public record Density(
            String level, long nodeCount, long totalDays, String averageNodesPerDay, String ruleVersion) {
    }

    public record WeatherAvailability(Boolean available, String status, String message) {
    }

    public record OngoingInsight(TodaySnapshot today, List<Suggestion> suggestions) {
    }

    public record TodaySnapshot(
            String date,
            long itineraryCount,
            String expenseAmount,
            String currency,
            long journalCount,
            long photoCount,
            List<SummaryResp.TimelineItineraryItem> todayNodes) {
    }

    public record Suggestion(
            String suggestionKey,
            String date,
            Map<String, Long> facts,
            String action,
            String targetDate) {
    }

    public record MemoryMaterial(
            long journalCount,
            long photoCount,
            long userFeaturedJournalCount,
            long userFeaturedPhotoCount,
            long systemRecommendedPhotoCount,
            List<DailyMaterial> dailyMaterials,
            List<RecentFragment> recentFragments,
            CoverState cover,
            DisplayableContent displayableContent) {
    }

    public record DailyMaterial(
            String date, int dayIndex, long itineraryCount, long journalCount, long photoCount) {
    }

    public record RecentFragment(
            String journalId, String date, boolean hasText, long photoCount) {
    }

    public record CoverState(boolean available, String source) {
    }

    public record DisplayableContent(List<String> sections, String presentation) {
    }

    public record CompletedInsight(
            String totalExpenseAmount,
            String averageDailyExpense,
            String currency,
            List<CategoryStat> categoryStats,
            List<DailyTrend> dailyTrend,
            Pace pace,
            Highlights highlights) {
    }

    public record Highlights(
            HighlightDay mostRelaxedDay,
            HighlightDay busiestDay,
            HighlightDay mostMemorableDay,
            RepresentativeLog representativeLog,
            FeaturedPhotoSummary featuredPhotos) {
    }

    public record HighlightDay(
            String date,
            int dayIndex,
            String label,
            long itineraryCount,
            long journalCount,
            long photoCount,
            String paceLevel) {
    }

    public record RepresentativeLog(
            String journalId,
            String date,
            String content,
            String locationName,
            boolean isHighlight,
            long photoCount) {
    }

    public record FeaturedPhotoSummary(
            long userFeaturedCount,
            long systemRecommendedCount,
            List<SummaryResp.PhotoItem> topPhotos) {
    }

    public record CategoryStat(
            String category, String categoryName, String amount, String percentage, long count) {
    }

    public record DailyTrend(
            String date,
            int dayIndex,
            long itineraryCount,
            long journalCount,
            long photoCount,
            String expenseAmount,
            String paceLevel) {
    }

    public record Pace(String level, String busiestDate, long busiestDayNodeCount, String ruleVersion) {
    }

    public record ActionItem(String type, String title, String tripId, String date) {
    }
}
