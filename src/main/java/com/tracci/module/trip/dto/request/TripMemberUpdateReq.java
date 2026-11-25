package com.tracci.module.trip.dto.request;

import jakarta.validation.constraints.NotBlank;

public record TripMemberUpdateReq(
        @NotBlank(message = "角色不能为空")
        String role
) {
}
