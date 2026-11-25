package com.tracci.module.location.controller;

import com.tracci.common.response.ApiResponse;
import com.tracci.module.location.dto.request.LocationPoiSearchReq;
import com.tracci.module.location.dto.response.LocationPoiSearchResp;
import com.tracci.module.location.service.LocationPoiService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/locations")
@RequiredArgsConstructor
public class LocationController {

    private final LocationPoiService locationPoiService;

    @GetMapping("/poi/search")
    public ApiResponse<LocationPoiSearchResp> searchPoi(@Valid LocationPoiSearchReq req) {
        return ApiResponse.ok(locationPoiService.search(req));
    }
}
