package com.tracci.module.journal.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.tracci.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@TableName("journal_entries")
public class JournalEntry extends BaseEntity {

    @TableField("trip_id")
    private String tripId;

    @TableField("user_id")
    private String userId;

    @TableField("content")
    private String content;

    @TableField("location_name")
    private String locationName;

    @TableField("latitude")
    private BigDecimal latitude;

    @TableField("longitude")
    private BigDecimal longitude;

    @TableField("location_address")
    private String locationAddress;

    @TableField("location_poi_id")
    private String locationPoiId;

    @TableField("location_adcode")
    private String locationAdcode;

    @TableField("location_city_code")
    private String locationCityCode;

    @TableField("location_province_name")
    private String locationProvinceName;

    @TableField("location_city_name")
    private String locationCityName;

    @TableField("location_district_name")
    private String locationDistrictName;

    @TableField("location_provider")
    private String locationProvider;

    @TableField("location_country")
    private String locationCountry;

    @TableField("occurred_at")
    private LocalDateTime occurredAt;

    @TableField("mood")
    private String mood;

    @TableField("is_highlight")
    private Boolean isHighlight;

    @TableField("sort_order")
    private Integer sortOrder;

    @TableField("visibility")
    private String visibility;
}
