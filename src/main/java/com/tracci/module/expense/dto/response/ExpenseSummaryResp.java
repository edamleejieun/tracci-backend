package com.tracci.module.expense.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ExpenseSummaryResp(
        String totalCny,
        String perDayCny,
        long expenseCount,
        List<CategoryBreakdown> breakdown
) {
    public record CategoryBreakdown(
            String category,
            String amountCny,
            long count,
            double ratio
    ) {
    }
}
