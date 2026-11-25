package com.tracci.module.expense.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record ExpenseCreateReq(
        @NotNull(message = "金额不能为空")
        @DecimalMin(value = "0.01", message = "金额必须大于 0")
        @Digits(integer = 10, fraction = 2, message = "金额最多 10 位整数和 2 位小数")
        BigDecimal amountInput,

        @NotBlank(message = "币种不能为空")
        @Size(min = 3, max = 3, message = "币种必须是 3 位 ISO 4217 代码")
        String currency,

        @NotBlank(message = "开支分类不能为空")
        String category,

        @Size(max = 200, message = "备注长度不能超过 200")
        String note,

        @NotNull(message = "消费时间不能为空")
        LocalDateTime spentAt,

        String expenseScope,

        String payerMemberId,

        List<String> participantMemberIds,

        String splitMethod,

        String linkedNodeId,

        String linkedStayGroupId
) {
    public ExpenseCreateReq(
            BigDecimal amountInput,
            String currency,
            String category,
            String note,
            LocalDateTime spentAt) {
        this(amountInput, currency, category, note, spentAt, null, null, null, null, null, null);
    }

    public ExpenseCreateReq(
            BigDecimal amountInput,
            String currency,
            String category,
            String note,
            LocalDateTime spentAt,
            String expenseScope,
            String payerMemberId,
            List<String> participantMemberIds,
            String splitMethod) {
        this(amountInput, currency, category, note, spentAt,
                expenseScope, payerMemberId, participantMemberIds, splitMethod, null, null);
    }
}
