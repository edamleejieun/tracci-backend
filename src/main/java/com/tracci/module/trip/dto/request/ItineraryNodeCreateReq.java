package com.tracci.module.trip.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;

import java.util.Map;

public record ItineraryNodeCreateReq(
        @Size(max = 100, message = "title 长度不能超过 100")
        String title,

        @NotBlank(message = "type 不能为空")
        String type,

        String startTime,

        String endTime,

        @Size(max = 200, message = "address 长度不能超过 200")
        String address,

        String note,

        @Valid
        ItineraryNodeLocationReq location,

        /**
         * 类型扩展字段。缺省 / null / {} 表示无扩展内容。
         * transport：kind、mode（必填）、flightNo/trainNo 等可选。
         * hotel：kind、confirmationCode/checkInTime/checkOutTime 可选。
         */
        Map<String, Object> details,

        @Size(max = 36, message = "savedPlaceId 长度不能超过 36")
        String savedPlaceId
) {
}
