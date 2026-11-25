package com.tracci.module.trip.dto.request;

import jakarta.validation.constraints.NotNull;

public record TripShareUpdateReq(
        @NotNull(message = "shareEnabled 不能为空")
        Boolean shareEnabled
) {
}
