package com.tracci.module.savedplace.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record LocationCandidate(
        @NotBlank(message = "地点名称不能为空")
        @Size(max = 200, message = "地点名称长度不能超过 200")
        String name,

        @Size(max = 500, message = "地址长度不能超过 500")
        String address,

        @Size(max = 20, message = "provider 长度不能超过 20")
        @JsonAlias("source")
        String provider,

        @Size(max = 200, message = "providerPlaceId 长度不能超过 200")
        @JsonAlias("poiId")
        String providerPlaceId,

        @Size(max = 100, message = "国家长度不能超过 100")
        String country,

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
        String cityCode
) {
}
