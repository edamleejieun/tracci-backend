package com.tracci.module.savedplace.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.tracci.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("saved_place_usages")
public class SavedPlaceUsage extends BaseEntity {

    @TableField("user_id")
    private String userId;

    @TableField("saved_place_id")
    private String savedPlaceId;

    @TableField("trip_id")
    private String tripId;

    @TableField("itinerary_node_id")
    private String itineraryNodeId;

    @TableField("usage_type")
    private String usageType;
}
