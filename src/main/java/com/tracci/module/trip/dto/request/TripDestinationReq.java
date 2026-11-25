package com.tracci.module.trip.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record TripDestinationReq(
        @Size(max = 100, message = "目的地名称长度不能超过 100")
        String name,

        @Size(max = 20, message = "目的地 adcode 长度不能超过 20")
        String adcode,

        @Size(max = 20, message = "目的地 cityCode 长度不能超过 20")
        String cityCode,

        @Size(max = 50, message = "目的地省份长度不能超过 50")
        String provinceName,

        @Size(max = 50, message = "目的地城市长度不能超过 50")
        String cityName,

        @Size(max = 50, message = "目的地区县长度不能超过 50")
        String districtName,

        @DecimalMin(value = "-90.0", message = "目的地纬度必须在 -90 到 90 之间")
        @DecimalMax(value = "90.0", message = "目的地纬度必须在 -90 到 90 之间")
        BigDecimal latitude,

        @DecimalMin(value = "-180.0", message = "目的地经度必须在 -180 到 180 之间")
        @DecimalMax(value = "180.0", message = "目的地经度必须在 -180 到 180 之间")
        BigDecimal longitude,

        @Size(max = 20, message = "destination provider 长度不能超过 20")
        String provider,

        @Size(max = 100, message = "providerPlaceId 长度不能超过 100")
        String providerPlaceId,

        @Size(max = 50, message = "国家长度不能超过 50")
        String country
) {
}
