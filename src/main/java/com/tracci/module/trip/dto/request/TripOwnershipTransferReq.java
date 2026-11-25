package com.tracci.module.trip.dto.request;

import jakarta.validation.constraints.NotBlank;

public record TripOwnershipTransferReq(
        @NotBlank String memberId
) {
}
