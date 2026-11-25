package com.tracci.module.expense.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.tracci.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 用户自定义开支分类（每趟旅行独立）。
 * 系统分类（food/transport/hotel/attraction/shopping/other）为硬编码枚举，不存此表。
 */
@Getter
@Setter
@TableName("expense_categories")
public class ExpenseCategoryEntity extends BaseEntity {

    @TableField("trip_id")
    private String tripId;

    @TableField("name")
    private String name;

    @TableField("sort_order")
    private Integer sortOrder;
}
