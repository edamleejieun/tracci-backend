package com.tracci.module.expense.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ExpenseLinkedItineraryResp(
        String nodeId,
        String stayGroupId,
        String title,
        String type
) {
}
