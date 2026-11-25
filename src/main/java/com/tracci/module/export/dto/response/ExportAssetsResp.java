package com.tracci.module.export.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ExportAssetsResp(
        TripInfo trip,
        Stats stats,
        List<PhotoItem> photos,
        List<String> visitedPlaces,
        List<ExpenseCategoryStat> expenseCategoryStats,
        Watermark watermark,
        ExportOptions exportOptions
) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record TripInfo(
            String id,
            String title,
            String destination,
            String startDate,
            String endDate,
            long totalDays,
            String coverPhoto
    ) {
    }

    public record Stats(
            long itineraryCount,
            long journalCount,
            long photoCount,
            long expenseCount,
            BigDecimal totalExpenseAmount,
            BigDecimal averageDailyExpense,
            String currency
    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PhotoItem(
            String fileId,
            String journalId,
            String url,
            String thumbnailUrl,
            String previewUrl,
            String exportUrl,
            Integer width,
            Integer height,
            Integer sortOrder
    ) {
    }

    public record ExpenseCategoryStat(
            String category,
            String categoryName,
            BigDecimal amount,
            BigDecimal percentage,
            long count
    ) {
    }

    public record Watermark(
            boolean enabled,
            String text
    ) {
    }

    public record ExportOptions(
            String template,
            String format,
            ContentOptions contentOptions
    ) {
    }

    public record ContentOptions(
            boolean showDate,
            boolean showDays,
            boolean showItineraryCount,
            boolean showJournalCount,
            boolean showExpense,
            boolean showVisitedPlaces,
            boolean showExpenseDistribution,
            boolean showWatermark,
            String copyStyle
    ) {
    }
}
