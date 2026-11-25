package com.tracci.module.trip.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.tracci.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@TableName("trip_members")
public class TripMember extends BaseEntity {

    @TableField("trip_id")
    private String tripId;

    @TableField("user_id")
    private String userId;

    @TableField("role")
    private String role;

    @TableField("status")
    private String status;

    @TableField("invited_by_user_id")
    private String invitedByUserId;

    @TableField("joined_at")
    private LocalDateTime joinedAt;
}
