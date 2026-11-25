package com.tracci.module.trip.controller;

import com.tracci.common.response.ApiResponse;
import com.tracci.common.response.PageResponse;
import com.tracci.module.trip.dto.request.TripCreateReq;
import com.tracci.module.trip.dto.request.TripUpdateReq;
import com.tracci.module.trip.dto.response.TripCreateResp;
import com.tracci.module.trip.dto.response.TripDetailResp;
import com.tracci.module.trip.dto.response.TripListResp;
import com.tracci.module.trip.service.TripService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/trips")
@RequiredArgsConstructor
public class TripController {

    private final TripService tripService;

    @PostMapping
    public ApiResponse<TripCreateResp> create(@Valid @RequestBody TripCreateReq req) {
        return ApiResponse.ok(tripService.createTrip(req));
    }

    @GetMapping
    public ApiResponse<PageResponse<TripListResp>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(tripService.listTrips(page, pageSize, status, keyword));
    }

    @GetMapping("/{tripId}")
    public ApiResponse<TripDetailResp> detail(@PathVariable String tripId) {
        return ApiResponse.ok(tripService.getTrip(tripId));
    }

    @PutMapping("/{tripId}")
    public ApiResponse<TripDetailResp> update(
            @PathVariable String tripId,
            @Valid @RequestBody TripUpdateReq req) {
        return ApiResponse.ok(tripService.updateTrip(tripId, req));
    }

    @DeleteMapping("/{tripId}")
    public ApiResponse<Void> delete(@PathVariable String tripId) {
        tripService.deleteTrip(tripId);
        return ApiResponse.ok();
    }
}
