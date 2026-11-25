package com.tracci.module.stats.dto.response;

import java.util.List;

public record StatsRecordsAnalysisResp(
        long journalCount,
        long photoCount,
        long itineraryCount,
        List<TripRecords> tripRecords
) {

    public record TripRecords(
            String tripId,
            String title,
            String destinationName,
            long itineraryCount,
            long journalCount,
            long photoCount,
            long expenseCount
    ) {
    }
}
