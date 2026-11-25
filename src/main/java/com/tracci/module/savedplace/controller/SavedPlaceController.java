package com.tracci.module.savedplace.controller;

import com.tracci.common.context.LoginUserContext;
import com.tracci.common.response.ApiResponse;
import com.tracci.common.response.PageResponse;
import com.tracci.module.savedplace.dto.request.CreateSavedPlaceRequest;
import com.tracci.module.savedplace.dto.request.UpdateSavedPlaceRequest;
import com.tracci.module.savedplace.dto.response.SavedPlaceSummary;
import com.tracci.module.savedplace.service.SavedPlaceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/saved-places")
@RequiredArgsConstructor
public class SavedPlaceController {

    private final SavedPlaceService savedPlaceService;

    @PostMapping
    public ApiResponse<SavedPlaceSummary> create(@Valid @RequestBody CreateSavedPlaceRequest request) {
        String userId = LoginUserContext.requireUserId();
        SavedPlaceSummary resp = savedPlaceService.createSavedPlace(userId, request);
        return ApiResponse.ok(resp);
    }

    @GetMapping
    public ApiResponse<PageResponse<SavedPlaceSummary>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String placeScope,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String usage,
            @RequestParam(required = false) String tripId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        String userId = LoginUserContext.requireUserId();
        PageResponse<SavedPlaceSummary> resp = savedPlaceService.listSavedPlaces(
                userId, keyword, placeScope, category, usage, tripId, page, pageSize);
        return ApiResponse.ok(resp);
    }

    @PutMapping("/{savedPlaceId}")
    public ApiResponse<SavedPlaceSummary> update(
            @PathVariable String savedPlaceId,
            @Valid @RequestBody UpdateSavedPlaceRequest request) {
        String userId = LoginUserContext.requireUserId();
        SavedPlaceSummary resp = savedPlaceService.updateSavedPlace(userId, savedPlaceId, request);
        return ApiResponse.ok(resp);
    }

    @DeleteMapping("/{savedPlaceId}")
    public ApiResponse<Void> delete(@PathVariable String savedPlaceId) {
        String userId = LoginUserContext.requireUserId();
        savedPlaceService.deleteSavedPlace(userId, savedPlaceId);
        return ApiResponse.ok();
    }
}
