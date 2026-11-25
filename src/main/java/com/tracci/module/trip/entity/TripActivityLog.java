package com.tracci.module.trip.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.tracci.common.entity.BaseEntity;
import com.tracci.common.mybatis.JsonbStringTypeHandler;
import lombok.Getter;
import lombok.Setter;
import org.apache.ibatis.type.JdbcType;

@Getter
@Setter
@TableName(value = "trip_activity_logs", autoResultMap = true)
public class TripActivityLog extends BaseEntity {

    @TableField("trip_id")
    private String tripId;

    @TableField("actor_user_id")
    private String actorUserId;

    @TableField("actor_member_id")
    private String actorMemberId;

    @TableField("action")
    private String action;

    @TableField("target_type")
    private String targetType;

    @TableField("target_id")
    private String targetId;

    @TableField(value = "field_changes_json", jdbcType = JdbcType.OTHER, typeHandler = JsonbStringTypeHandler.class)
    private String fieldChangesJson;

    @TableField("summary")
    private String summary;
}
