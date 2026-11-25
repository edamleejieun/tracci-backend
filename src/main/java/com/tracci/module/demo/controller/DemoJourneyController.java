package com.tracci.module.demo.controller;

import com.tracci.common.response.ApiResponse;
import com.tracci.module.demo.dto.response.DemoJourneyBundleResp;
import com.tracci.module.demo.service.DemoJourneyDelivery;
import com.tracci.module.demo.service.DemoJourneyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/demo/journey")
@RequiredArgsConstructor
public class DemoJourneyController {

    private static final String PRIVATE_REVALIDATE =
            "private, no-cache, max-age=0, must-revalidate";

    private final DemoJourneyService demoJourneyService;

    @GetMapping
    public ResponseEntity<ApiResponse<DemoJourneyBundleResp>> getJourney(
            @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false)
            String ifNoneMatch) {
        DemoJourneyDelivery delivery = demoJourneyService.getJourney(ifNoneMatch);
        if (delivery.notModified()) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                    .header(HttpHeaders.CACHE_CONTROL, PRIVATE_REVALIDATE)
                    .eTag(delivery.eTag())
                    .build();
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, PRIVATE_REVALIDATE)
                .eTag(delivery.eTag())
                .body(ApiResponse.ok(delivery.bundle()));
    }
}
