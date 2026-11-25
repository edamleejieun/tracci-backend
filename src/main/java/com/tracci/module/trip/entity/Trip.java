package com.tracci.module.trip.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.tracci.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@TableName("trips")
public class Trip extends BaseEntity {

    @TableField("user_id")
    private String userId;

    @TableField("title")
    private String title;

    @TableField("cover_file_id")
    private String coverFileId;

    @TableField("destination_name")
    private String destinationName;

    @TableField("destination_adcode")
    private String destinationAdcode;

    @TableField("destination_city_code")
    private String destinationCityCode;

    @TableField("destination_province_name")
    private String destinationProvinceName;

    @TableField("destination_city_name")
    private String destinationCityName;

    @TableField("destination_district_name")
    private String destinationDistrictName;

    @TableField("destination_latitude")
    private BigDecimal destinationLatitude;

    @TableField("destination_longitude")
    private BigDecimal destinationLongitude;

    @TableField("destination_provider")
    private String destinationProvider;

    @TableField("destination_provider_place_id")
    private String destinationProviderPlaceId;

    @TableField("destination_country")
    private String destinationCountry;

    @TableField("destination_place_id")
    private String destinationPlaceId;

    @TableField("start_date")
    private LocalDate startDate;

    @TableField("end_date")
    private LocalDate endDate;

    @TableField("status")
    private String status;

    @TableField("base_currency")
    private String baseCurrency;

    @TableField("share_token")
    private String shareToken;

    @TableField("share_enabled")
    private Boolean shareEnabled;
}
