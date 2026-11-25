package com.tracci.module.placecontent.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * 管理端更新 Place 身份字段。DESTINATION 改名时可级联 POI.city_name。
 */
public record PlaceAdminUpdateRequest(
        @Size(max = 200, message = "地点名称长度不能超过 200")
        String canonicalName,

        @Size(max = 100, message = "城市长度不能超过 100")
        String cityName,

        @Size(max = 100, message = "国家长度不能超过 100")
        String country,

        @Size(max = 20, message = "category 长度不能超过 20")
        String category,

        @Size(max = 500, message = "地址长度不能超过 500")
        String address,

        @DecimalMin(value = "-90.0", message = "纬度必须在 -90 到 90 之间")
        @DecimalMax(value = "90.0", message = "纬度必须在 -90 到 90 之间")
        BigDecimal latitude,

        @DecimalMin(value = "-180.0", message = "经度必须在 -180 到 180 之间")
        @DecimalMax(value = "180.0", message = "经度必须在 -180 到 180 之间")
        BigDecimal longitude,

        Boolean cascadeCityRename
) {
}
