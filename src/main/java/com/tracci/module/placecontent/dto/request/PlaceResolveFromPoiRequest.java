package com.tracci.module.placecontent.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * 管理端从高德 POI 解析/创建业务 Place 的请求。
 * poiId 会被服务端重新核验（名称、坐标、地址、typecode 均以高德详情为准），
 * 命中已有 (AMAP, providerPlaceId) 身份时复用同一 Place，不产生重复。
 */
public record PlaceResolveFromPoiRequest(
        @NotBlank(message = "poiId 不能为空")
        @Size(max = 200, message = "poiId 长度不能超过 200")
        String poiId,

        @NotBlank(message = "地点名称不能为空")
        @Size(max = 200, message = "地点名称长度不能超过 200")
        String name,

        @Size(max = 500, message = "地址长度不能超过 500")
        String address,

        @Size(max = 100, message = "城市长度不能超过 100")
        String cityName,

        @DecimalMin(value = "-90.0", message = "纬度必须在 -90 到 90 之间")
        @DecimalMax(value = "90.0", message = "纬度必须在 -90 到 90 之间")
        BigDecimal latitude,

        @DecimalMin(value = "-180.0", message = "经度必须在 -180 到 180 之间")
        @DecimalMax(value = "180.0", message = "经度必须在 -180 到 180 之间")
        BigDecimal longitude,

        @Size(max = 20, message = "adcode 长度不能超过 20")
        String adcode,

        @Size(max = 20, message = "cityCode 长度不能超过 20")
        String cityCode,

        @Size(max = 36, message = "destinationPlaceId 长度不能超过 36")
        String destinationPlaceId
) {
}
