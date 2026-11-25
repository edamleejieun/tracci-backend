package com.tracci.module.export.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ItineraryExportAssetsResp(
        TripInfo trip,
        List<DayItem> days,
        Meta meta,
        Watermark watermark
) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record TripInfo(
            String id,
            String title,
            String destination,
            String startDate,
            String endDate,
            long totalDays
    ) {
    }

    public record DayItem(
            String id,
            int dayIndex,
            String date,
            long nodeCount,
            List<NodeItem> nodes
    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record NodeItem(
            String id,
            String title,
            String type,
            String typeName,
            String startTime,
            String endTime,
            String locationName,
            String locationAddress,
            String note,
            Integer sortOrder,
            /** Phase 3：交通/住宿 details 摘要 */
            String detailSummary
    ) {
        public NodeItem(
                String id,
                String title,
                String type,
                String typeName,
                String startTime,
                String endTime,
                String locationName,
                String locationAddress,
                String note,
                Integer sortOrder) {
            this(id, title, type, typeName, startTime, endTime, locationName, locationAddress, note, sortOrder, null);
        }
    }

    public record Meta(
            long totalDayCount,
            long totalNodeCount,
            long selectedDayCount,
            long selectedNodeCount
    ) {
    }

    public record Watermark(
            boolean enabled,
            String text
    ) {
    }
}
