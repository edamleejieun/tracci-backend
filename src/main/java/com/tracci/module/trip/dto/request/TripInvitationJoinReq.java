package com.tracci.module.trip.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TripInvitationJoinReq(
        @NotBlank(message = "邀请码不能为空")
        @Size(min = 4, max = 4, message = "邀请码长度必须为 4 位")
        String inviteCode
) {
}
