package com.tracci.module.trip.dto.request;

import jakarta.validation.constraints.NotNull;

import java.util.List;

public record ItineraryNodeReorderReq(
        @NotNull(message = "nodeIds 不能为空")
        List<String> nodeIds
) {
}
