package com.tracci.module.trip.controller;

import com.tracci.common.response.ApiResponse;
import com.tracci.module.trip.dto.request.ItineraryNodeCreateReq;
import com.tracci.module.trip.dto.request.ItineraryNodeReorderReq;
import com.tracci.module.trip.dto.request.ItineraryNodeUpdateReq;
import com.tracci.module.trip.dto.response.ItineraryNodeResp;
import com.tracci.module.trip.dto.response.ItineraryResp;
import com.tracci.module.trip.service.ItineraryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/trips")
@RequiredArgsConstructor
public class ItineraryController {

    private final ItineraryService itineraryService;

    @GetMapping("/{tripId}/itinerary")
    public ApiResponse<ItineraryResp> getItinerary(@PathVariable String tripId) {
        return ApiResponse.ok(itineraryService.getItinerary(tripId));
    }

    @PostMapping("/{tripId}/days/{dayId}/nodes")
    public ApiResponse<ItineraryNodeResp> createNode(
            @PathVariable String tripId,
            @PathVariable String dayId,
            @Valid @RequestBody ItineraryNodeCreateReq req) {
        return ApiResponse.ok(itineraryService.createNode(tripId, dayId, req));
    }

    @PutMapping("/{tripId}/days/{dayId}/nodes/{nodeId}")
    public ApiResponse<ItineraryNodeResp> updateNode(
            @PathVariable String tripId,
            @PathVariable String dayId,
            @PathVariable String nodeId,
            @Valid @RequestBody ItineraryNodeUpdateReq req) {
        return ApiResponse.ok(itineraryService.updateNode(tripId, dayId, nodeId, req));
    }

    @DeleteMapping("/{tripId}/days/{dayId}/nodes/{nodeId}")
    public ApiResponse<Void> deleteNode(
            @PathVariable String tripId,
            @PathVariable String dayId,
            @PathVariable String nodeId) {
        itineraryService.deleteNode(tripId, dayId, nodeId);
        return ApiResponse.ok();
    }

    @PutMapping("/{tripId}/days/{dayId}/nodes/reorder")
    public ApiResponse<Void> reorderNodes(
            @PathVariable String tripId,
            @PathVariable String dayId,
            @Valid @RequestBody ItineraryNodeReorderReq req) {
        itineraryService.reorderNodes(tripId, dayId, req);
        return ApiResponse.ok();
    }
}
