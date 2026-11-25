package com.tracci.module.export.controller;

import com.tracci.common.response.ApiResponse;
import com.tracci.module.export.dto.response.ExportAssetsResp;
import com.tracci.module.export.service.ExportService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/trips/{tripId}/export-assets")
@RequiredArgsConstructor
public class ExportController {

    private final ExportService exportService;

    @GetMapping
    public ApiResponse<ExportAssetsResp> getExportAssets(
            @PathVariable String tripId,
            @RequestParam(required = false) String template,
            @RequestParam(required = false) String format) {
        return ApiResponse.ok(exportService.getExportAssets(tripId, template, format));
    }
}
