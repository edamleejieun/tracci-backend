package com.tracci.module.trip.dto.request;

import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;

import java.util.Map;

public record ItineraryNodeUpdateReq(
        @Size(max = 100, message = "title 长度不能超过 100")
        String title,

        String type,

        String startTime,

        String endTime,

        @Size(max = 200, message = "address 长度不能超过 200")
        String address,

        String note,

        @Valid
        ItineraryNodeLocationReq location,

        /**
         * 类型扩展字段。null=不修改；非 null（含 {}）=整体替换。
         */
        Map<String, Object> details
) {
}
