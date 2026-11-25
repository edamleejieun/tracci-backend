package com.tracci.module.demo.service;

import com.tracci.common.context.LoginUserContext;
import com.tracci.integration.cos.MockCosClient;
import com.tracci.module.demo.config.DemoJourneyProperties;
import com.tracci.module.demo.controller.DemoJourneyController;
import com.tracci.module.demo.fixture.DemoJourneyFixtureFactory;
import com.tracci.module.demo.validation.DemoJourneyCoverageValidator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DemoJourneyHttpContractTest {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-07-30T02:00:00Z"), ZoneOffset.UTC);

    @AfterEach
    void tearDown() {
        LoginUserContext.clear();
    }

    @Test
    void conditionalGet_returnsFullApiResponseThenBodyless304() throws Exception {
        DemoJourneyProperties demoProperties = new DemoJourneyProperties();
        demoProperties.setEnabled(true);
        demoProperties.setSchemaVersion(1);
        demoProperties.setContentVersion("2026.07.31.7");
        demoProperties.setCosObjectKeyPrefix("system/demo-journey/v1");
        var cosProperties = com.tracci.module.demo.contract.DemoJourneyContractSupport.cosProperties();
        var provider = new DemoJourneyFixtureFactory(
                demoProperties,
                new DemoJourneyCoverageValidator(),
                new MockCosClient("test-bucket", "ap-guangzhou"),
                cosProperties,
                com.tracci.module.demo.contract.DemoJourneyContractSupport.ciUrlBuilder());
        var service = new DemoJourneyServiceImpl(
                demoProperties,
                List.of(provider),
                cosProperties,
                FIXED_CLOCK);
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new DemoJourneyController(service))
                .build();
        LoginUserContext.setUserId("contract-user");

        String eTag = mockMvc.perform(get("/api/v1/demo/journey"))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        HttpHeaders.CACHE_CONTROL,
                        "private, no-cache, max-age=0, must-revalidate"))
                .andExpect(header().string(HttpHeaders.ETAG, startsWith("W/\"demo-")))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.schemaVersion").value(1))
                .andExpect(jsonPath("$.data.scenes.planning").exists())
                .andExpect(jsonPath("$.data.scenes.ongoing").exists())
                .andExpect(jsonPath("$.data.scenes.completed").exists())
                .andReturn()
                .getResponse()
                .getHeader(HttpHeaders.ETAG);

        mockMvc.perform(get("/api/v1/demo/journey").header(HttpHeaders.IF_NONE_MATCH, eTag))
                .andExpect(status().isNotModified())
                .andExpect(header().string(HttpHeaders.ETAG, eTag))
                .andExpect(header().string(
                        HttpHeaders.CACHE_CONTROL,
                        "private, no-cache, max-age=0, must-revalidate"))
                .andExpect(content().string(""));
    }
}
