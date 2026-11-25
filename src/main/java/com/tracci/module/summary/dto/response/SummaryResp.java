package com.tracci.module.summary.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record SummaryResp(
        TripInfo trip,
        Stats stats,
        ExpenseSummary expenseSummary,
        List<LogItem> highlightLogs,
        List<PhotoItem> userFeaturedPhotos,
        List<PhotoItem> recommendedPhotos,
        List<DailyTimelineItem> dailyTimeline
) {

    public SummaryResp(
            TripInfo trip,
            Stats stats,
            ExpenseSummary expenseSummary,
            List<LogItem> highlightLogs,
            List<PhotoItem> recommendedPhotos,
            List<DailyTimelineItem> dailyTimeline) {
        this(trip, stats, expenseSummary, highlightLogs, List.of(), recommendedPhotos, dailyTimeline);
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record TripInfo(
            String id,
            String title,
            String destination,
            String startDate,
            String endDate,
            long dayCount,
            String status,
            CoverPhoto coverPhoto
    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CoverPhoto(
            String fileId,
            String url,
            String thumbnailUrl,
            String previewUrl,
            String exportUrl,
            String storyPortraitUrl,
            String storyCardUrl,
            String squareUrl,
            String watermarkedUrl,
            Integer width,
            Integer height
    ) {
    }

    public record Stats(
            long dayCount,
            long itineraryCount,
            long journalCount,
            long photoCount,
            long expenseCount,
            String totalExpenseAmount,
            String currency,
            long highlightCount
    ) {
    }

    public record ExpenseSummary(
            String totalAmount,
            String currency,
            List<ExpenseCategoryStat> categoryStats
    ) {
    }

    public record ExpenseCategoryStat(
            String category,
            String categoryName,
            String amount,
            String percentage,
            long count
    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record LogItem(
            String id,
            String content,
            String locationName,
            String logTime,
            Boolean isHighlight,
            List<PhotoItem> photos
    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PhotoItem(
            String id,
            String fileId,
            String journalId,
            String url,
            String thumbnailUrl,
            String previewUrl,
            String exportUrl,
            String storyPortraitUrl,
            String storyCardUrl,
            String squareUrl,
            String watermarkedUrl,
            Integer width,
            Integer height,
            Integer sortOrder,
            boolean userFeatured
    ) {
        public PhotoItem(
                String fileId,
                String journalId,
                String url,
                String thumbnailUrl,
                String previewUrl,
                String exportUrl,
                String storyPortraitUrl,
                String storyCardUrl,
                String squareUrl,
                String watermarkedUrl,
                Integer width,
                Integer height,
                Integer sortOrder) {
            this(null, fileId, journalId, url, thumbnailUrl, previewUrl, exportUrl, storyPortraitUrl, storyCardUrl,
                    squareUrl, watermarkedUrl, width, height, sortOrder, false);
        }
    }

    public record DailyTimelineItem(
            String date,
            int dayIndex,
            List<TimelineItineraryItem> itineraries,
            List<LogItem> logs
    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record TimelineItineraryItem(
            String id,
            String type,
            String title,
            String locationName,
            String startTime,
            String endTime,
            /** Phase 3：交通/住宿 details 摘要（如航班号、第 n/N 晚） */
            String detailSummary
    ) {
        /** 兼容旧调用点（无 detailSummary） */
        public TimelineItineraryItem(
                String id,
                String type,
                String title,
                String locationName,
                String startTime,
                String endTime) {
            this(id, type, title, locationName, startTime, endTime, null);
        }
    }
}
