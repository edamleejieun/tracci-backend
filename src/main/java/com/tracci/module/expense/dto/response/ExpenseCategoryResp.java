package com.tracci.module.expense.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ExpenseCategoryResp(
        String id,
        String tripId,
        String name,
        Integer sortOrder
) {
}
