package com.tracci.module.trip.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.tracci.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@TableName("trip_days")
public class TripDay extends BaseEntity {

    @TableField("trip_id")
    private String tripId;

    @TableField("user_id")
    private String userId;

    @TableField("day_index")
    private Integer dayIndex;

    @TableField("date")
    private LocalDate date;
}
