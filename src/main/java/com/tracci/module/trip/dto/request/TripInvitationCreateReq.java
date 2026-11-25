package com.tracci.module.trip.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record TripInvitationCreateReq(
        String roleToGrant,

        @Min(value = 1, message = "expiresInHours 至少为 1")
        @Max(value = 720, message = "expiresInHours 不能超过 720")
        Integer expiresInHours,

        @Min(value = 1, message = "maxUses 至少为 1")
        @Max(value = 100, message = "maxUses 不能超过 100")
        Integer maxUses
) {
}
