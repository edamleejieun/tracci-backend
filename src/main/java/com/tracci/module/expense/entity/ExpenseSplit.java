package com.tracci.module.expense.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.tracci.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@TableName("expense_splits")
public class ExpenseSplit extends BaseEntity {

    @TableField("expense_id")
    private String expenseId;

    @TableField("trip_id")
    private String tripId;

    @TableField("member_id")
    private String memberId;

    @TableField("user_id")
    private String userId;

    @TableField("share_amount_cny")
    private BigDecimal shareAmountCny;

    @TableField("share_amount_input")
    private BigDecimal shareAmountInput;
}
