package com.tracci.module.placecontent.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * 管理端直接创建业务 Place。DESTINATION 用于探索首页城市；POI 必须挂到已有城市。
 */
public record PlaceAdminCreateRequest(
        @NotBlank(message = "placeScope 不能为空")
        @Size(max = 20, message = "placeScope 长度不能超过 20")
        String placeScope,

        @NotBlank(message = "category 不能为空")
        @Size(max = 20, message = "category 长度不能超过 20")
        String category,

        @NotBlank(message = "地点名称不能为空")
        @Size(max = 200, message = "地点名称长度不能超过 200")
        String name,

        @Size(max = 100, message = "国家长度不能超过 100")
        String country,

        @Size(max = 100, message = "城市长度不能超过 100")
        String cityName,

        @Size(max = 36, message = "destinationPlaceId 长度不能超过 36")
        String destinationPlaceId,

        @Size(max = 500, message = "地址长度不能超过 500")
        String address,

        @DecimalMin(value = "-90.0", message = "纬度必须在 -90 到 90 之间")
        @DecimalMax(value = "90.0", message = "纬度必须在 -90 到 90 之间")
        BigDecimal latitude,

        @DecimalMin(value = "-180.0", message = "经度必须在 -180 到 180 之间")
        @DecimalMax(value = "180.0", message = "经度必须在 -180 到 180 之间")
        BigDecimal longitude
) {
}
