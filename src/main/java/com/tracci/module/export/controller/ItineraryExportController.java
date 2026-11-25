package com.tracci.module.export.controller;

import com.tracci.common.response.ApiResponse;
import com.tracci.module.export.dto.response.ItineraryExportAssetsResp;
import com.tracci.module.export.service.ExportService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/trips/{tripId}/itinerary-export-assets")
@RequiredArgsConstructor
public class ItineraryExportController {

    private final ExportService exportService;

    @GetMapping
    public ApiResponse<ItineraryExportAssetsResp> getItineraryExportAssets(
            @PathVariable String tripId,
            @RequestParam(required = false) String dates) {
        return ApiResponse.ok(exportService.getItineraryExportAssets(tripId, dates));
    }
}
