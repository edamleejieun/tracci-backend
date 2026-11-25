package com.tracci.module.location.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record LocationPoiSearchReq(
        @NotBlank(message = "keyword 不能为空")
        @Size(max = 50, message = "keyword 长度不能超过 50")
        String keyword,

        @Size(max = 50, message = "city 长度不能超过 50")
        String city,

        @DecimalMin(value = "-90.0", message = "latitude 必须在 -90 到 90 之间")
        @DecimalMax(value = "90.0", message = "latitude 必须在 -90 到 90 之间")
        BigDecimal latitude,

        @DecimalMin(value = "-180.0", message = "longitude 必须在 -180 到 180 之间")
        @DecimalMax(value = "180.0", message = "longitude 必须在 -180 到 180 之间")
        BigDecimal longitude,

        @Min(value = 1, message = "page 必须大于等于 1")
        Integer page,

        @Min(value = 1, message = "size 必须大于等于 1")
        @Max(value = 20, message = "size 不能超过 20")
        Integer size,

        @Size(max = 20, message = "clientPlatform 长度不能超过 20")
        String clientPlatform
) {
}
