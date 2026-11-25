package com.tracci.module.place.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.tracci.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@TableName("place_provider_refs")
public class PlaceProviderRef extends BaseEntity {

    @TableField("place_id")
    private String placeId;

    @TableField("provider")
    private String provider;

    @TableField("provider_place_id")
    private String providerPlaceId;

    @TableField("is_primary")
    private Boolean isPrimary;

    @TableField("last_checked_at")
    private LocalDateTime lastCheckedAt;
}
