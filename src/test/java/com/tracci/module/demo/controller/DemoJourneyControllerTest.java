package com.tracci.module.demo.controller;

import com.tracci.common.response.ApiResponse;
import com.tracci.module.demo.dto.response.DemoJourneyBundleResp;
import com.tracci.module.demo.service.DemoJourneyDelivery;
import com.tracci.module.demo.service.DemoJourneyService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DemoJourneyControllerTest {

    @Mock
    private DemoJourneyService demoJourneyService;

    @Test
    void getJourney_wrapsBundleWithPrivateRevalidationHeaders() {
        DemoJourneyBundleResp bundle =
                DemoJourneyBundleResp.disabled(1, "2026.07.31.7", "2026-07-30");
        String eTag = "W/\"demo-123\"";
        when(demoJourneyService.getJourney(null))
                .thenReturn(DemoJourneyDelivery.body(eTag, bundle));
        DemoJourneyController controller = new DemoJourneyController(demoJourneyService);

        ResponseEntity<ApiResponse<DemoJourneyBundleResp>> response = controller.getJourney(null);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(
                "private, no-cache, max-age=0, must-revalidate",
                response.getHeaders().getCacheControl());
        assertEquals(eTag, response.getHeaders().getETag());
        assertNotNull(response.getBody());
        assertEquals(0, response.getBody().code());
        assertSame(bundle, response.getBody().data());
    }

    @Test
    void getJourney_returns304WithoutBodyWhenETagMatches() {
        String eTag = "W/\"demo-123\"";
        when(demoJourneyService.getJourney(eTag))
                .thenReturn(DemoJourneyDelivery.notModified(eTag));
        DemoJourneyController controller = new DemoJourneyController(demoJourneyService);

        ResponseEntity<ApiResponse<DemoJourneyBundleResp>> response =
                controller.getJourney(eTag);

        assertEquals(304, response.getStatusCode().value());
        assertEquals(eTag, response.getHeaders().getETag());
        assertEquals(
                "private, no-cache, max-age=0, must-revalidate",
                response.getHeaders().getCacheControl());
        assertEquals(null, response.getBody());
    }
}
