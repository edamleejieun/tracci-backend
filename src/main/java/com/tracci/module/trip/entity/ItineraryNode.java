package com.tracci.module.trip.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.tracci.common.entity.BaseEntity;
import com.tracci.common.mybatis.JsonbStringTypeHandler;
import lombok.Getter;
import lombok.Setter;
import org.apache.ibatis.type.JdbcType;

import java.math.BigDecimal;
import java.time.LocalTime;

@Getter
@Setter
@TableName(value = "itinerary_nodes", autoResultMap = true)
public class ItineraryNode extends BaseEntity {

    @TableField("trip_day_id")
    private String tripDayId;

    @TableField("trip_id")
    private String tripId;

    @TableField("user_id")
    private String userId;

    @TableField("title")
    private String title;

    @TableField("type")
    private String type;

    @TableField("start_time")
    private LocalTime startTime;

    @TableField("end_time")
    private LocalTime endTime;

    @TableField("address")
    private String address;

    @TableField("location_name")
    private String locationName;

    @TableField("location_address")
    private String locationAddress;

    @TableField("location_latitude")
    private BigDecimal locationLatitude;

    @TableField("location_longitude")
    private BigDecimal locationLongitude;

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

    @TableField("location_place_id")
    private String locationPlaceId;

    @TableField("note")
    private String note;

    /**
     * 类型扩展 JSON 文本（PostgreSQL JSONB，经 {@link JsonbStringTypeHandler} 读写）。
     * 空扩展内容为 "{}"。
     */
    @TableField(value = "details_json", jdbcType = JdbcType.OTHER, typeHandler = JsonbStringTypeHandler.class)
    private String detailsJson;

    @TableField("sort_order")
    private Integer sortOrder;
}
