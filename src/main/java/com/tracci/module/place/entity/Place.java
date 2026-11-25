package com.tracci.module.place.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.tracci.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@TableName("places")
public class Place extends BaseEntity {

    @TableField("place_scope")
    private String placeScope;

    @TableField("category")
    private String category;

    @TableField("canonical_name")
    private String canonicalName;

    @TableField("address")
    private String address;

    @TableField("city_name")
    private String cityName;

    @TableField("country")
    private String country;

    @TableField("latitude")
    private BigDecimal latitude;

    @TableField("longitude")
    private BigDecimal longitude;

    @TableField("destination_key")
    private String destinationKey;

    @TableField("parent_place_id")
    private String parentPlaceId;

    @TableField("created_source")
    private String createdSource;

    @TableField("status")
    private String status;

    @TableField("merged_into_id")
    private String mergedIntoId;
}
