package com.tracci.module.expense.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.tracci.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@TableName("expenses")
public class Expense extends BaseEntity {

    @TableField("trip_id")
    private String tripId;

    @TableField("user_id")
    private String userId;

    @TableField("amount_input")
    private BigDecimal amountInput;

    @TableField("currency")
    private String currency;

    @TableField("amount_cny")
    private BigDecimal amountCny;

    @TableField("exchange_rate")
    private BigDecimal exchangeRate;

    @TableField("category")
    private String category;

    @TableField("note")
    private String note;

    @TableField("spent_at")
    private LocalDateTime spentAt;

    @TableField("expense_scope")
    private String expenseScope;

    @TableField("payer_member_id")
    private String payerMemberId;

    @TableField("created_by_member_id")
    private String createdByMemberId;

    @TableField("split_method")
    private String splitMethod;

    @TableField("linked_node_id")
    private String linkedNodeId;

    @TableField("linked_stay_group_id")
    private String linkedStayGroupId;
}
