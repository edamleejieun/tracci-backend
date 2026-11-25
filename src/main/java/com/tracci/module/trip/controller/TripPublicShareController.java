package com.tracci.module.trip.controller;

import com.tracci.common.response.ApiResponse;
import com.tracci.module.trip.dto.request.TripShareUpdateReq;
import com.tracci.module.trip.dto.response.PublicCoEditorInviteResp;
import com.tracci.module.trip.dto.response.PublicJourneyShareResp;
import com.tracci.module.trip.dto.response.TripShareResp;
import com.tracci.module.trip.service.TripPublicShareService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class TripPublicShareController {

    private final TripPublicShareService shareService;

    @PutMapping("/api/v1/trips/{tripId}/share")
    public ApiResponse<TripShareResp> updateShare(
            @PathVariable String tripId,
            @Valid @RequestBody TripShareUpdateReq req) {
        return ApiResponse.ok(shareService.updateShare(tripId, req));
    }

    @GetMapping("/api/v1/public/journeys/{tripId}/share")
    public ApiResponse<PublicJourneyShareResp> getJourneyShare(@PathVariable String tripId) {
        return ApiResponse.ok(shareService.getJourneyShare(tripId));
    }

    @GetMapping("/api/v1/public/co-editor/validate")
    public ApiResponse<PublicCoEditorInviteResp> validateCoEditorInvite(
            @RequestParam("id") String tripId,
            @RequestParam("code") String code) {
        return ApiResponse.ok(shareService.validateCoEditorInvite(tripId, code));
    }
}
