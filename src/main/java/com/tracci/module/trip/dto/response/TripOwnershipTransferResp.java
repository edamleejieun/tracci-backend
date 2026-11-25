package com.tracci.module.trip.dto.response;

public record TripOwnershipTransferResp(
        String tripId,
        String previousOwnerMemberId,
        String newOwnerMemberId,
        String newOwnerUserId,
        TripAccessResp access
) {
}
