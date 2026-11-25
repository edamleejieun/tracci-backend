package com.tracci.module.trip.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.tracci.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@TableName("trip_invitations")
public class TripInvitation extends BaseEntity {

    @TableField("trip_id")
    private String tripId;

    @TableField("created_by_user_id")
    private String createdByUserId;

    @TableField("invite_code_hash")
    private String inviteCodeHash;

    @TableField("role_to_grant")
    private String roleToGrant;

    @TableField("expires_at")
    private LocalDateTime expiresAt;

    @TableField("max_uses")
    private Integer maxUses;

    @TableField("used_count")
    private Integer usedCount;
}
