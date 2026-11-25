package com.tracci.module.expense.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ExpenseResp(
        String id,
        String tripId,
        String amountInput,
        String currency,
        String amountCny,
        String exchangeRate,
        String category,
        String note,
        String spentAt,
        String expenseScope,
        String payerMemberId,
        List<String> participantMemberIds,
        String createdByMemberId,
        String splitMethod,
        String linkedNodeId,
        String linkedStayGroupId,
        ExpenseLinkedItineraryResp linkedItinerary,
        String createdAt
) {
}
