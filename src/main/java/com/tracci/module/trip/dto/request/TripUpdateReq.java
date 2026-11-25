package com.tracci.module.trip.dto.request;

import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;

import java.time.LocalDate;

public record TripUpdateReq(
        @Size(max = 100, message = "旅行名称长度不能超过 100")
        String title,

        LocalDate startDate,

        LocalDate endDate,

        @Size(max = 36, message = "封面文件 ID 长度不能超过 36")
        String coverFileId,

        @Size(min = 3, max = 3, message = "主结算币种必须是 3 位 ISO 4217 代码")
        String baseCurrency,

        @Valid
        TripDestinationReq destination,

        /** 客户端已就「修改日期会改变旅程状态」获得用户确认 */
        Boolean statusChangeConfirmed
) {
}
