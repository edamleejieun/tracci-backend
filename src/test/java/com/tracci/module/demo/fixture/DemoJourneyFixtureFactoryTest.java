package com.tracci.module.demo.fixture;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tracci.integration.cos.CiProperties;
import com.tracci.integration.cos.CiUrlBuilder;
import com.tracci.integration.cos.CosProperties;
import com.tracci.integration.cos.MockCosClient;
import com.tracci.module.demo.config.DemoJourneyProperties;
import com.tracci.module.demo.dto.response.DemoJourneyBundleResp;
import com.tracci.module.demo.dto.response.DemoJourneyCapabilitiesResp;
import com.tracci.module.demo.validation.DemoJourneyCoverageValidator;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DemoJourneyFixtureFactoryTest {

    private static final LocalDate REFERENCE_DATE = LocalDate.of(2026, 7, 30);
    private static final String COS_BUCKET = "test-bucket";
    private static final String COS_REGION = "ap-guangzhou";
    private static final String COS_OBJECT_KEY_PREFIX = "system/demo-journey/v1";

    @Test
    void build_createsThreeValidatedScenesWithAllStatsResponses() throws Exception {
        DemoJourneyFixtureFactory factory = factory(properties());

        DemoJourneyBundleResp bundle = factory.build(REFERENCE_DATE, 0L);

        assertTrue(bundle.enabled());
        assertEquals("2026-07-30", bundle.referenceDate());
        assertNotNull(bundle.scenes().planning());
        assertNotNull(bundle.scenes().ongoing());
        assertNotNull(bundle.scenes().completed());
        assertEquals("planning", bundle.scenes().planning().trip().status());
        assertEquals("ongoing", bundle.scenes().ongoing().trip().status());
        assertEquals("completed", bundle.scenes().completed().trip().status());
        assertTrue(bundle.scenes().planning().journals().list().isEmpty());
        assertTrue(bundle.scenes().planning().expenses().list().isEmpty());
        assertEquals("0.00", bundle.scenes().planning().expenseSummary().totalCny());
        // Ongoing window is Day1=ref-3 … Day5=ref+1; default ref is Day4 (Disney).
        assertEquals("2026-07-27", bundle.scenes().ongoing().trip().startDate());
        assertEquals("2026-07-31", bundle.scenes().ongoing().trip().endDate());
        assertEquals(5, bundle.scenes().ongoing().trip().totalDays());
        assertEquals("上海", bundle.scenes().ongoing().trip().destination().name());
        assertTrue(bundle.scenes().ongoing().journals().list().stream()
                .noneMatch(journal -> journal.occurredAt().startsWith("2026-07-31")));
        String coverUrl = bundle.scenes().ongoing().trip().coverPhoto().url();
        assertTrue(coverUrl.startsWith(
                "https://" + COS_BUCKET + ".cos." + COS_REGION + ".myqcloud.com/"
                        + COS_OBJECT_KEY_PREFIX + "/"));
        assertTrue(coverUrl.contains("/cover/hero.png?"));
        assertTrue(coverUrl.contains("mock-access-signature=true"));

        var stats = bundle.scenes().ongoing().stats();
        assertNotNull(stats.overview());
        assertNotNull(stats.spendAnalysis());
        assertNotNull(stats.recordsAnalysis());
        assertNotNull(stats.footprintAnalysis());
        assertEquals(22, stats.overview().overview().itineraryCount());
        assertEquals(9, stats.overview().overview().journalCount());
        assertEquals(14, stats.overview().overview().expenseCount());
        assertEquals(9, stats.overview().overview().photoCount());
        assertEquals("9737.00", stats.spendAnalysis().totalAmount());
        assertEquals(3, stats.overview().insights().size());
        assertEquals(2, stats.overview().analysisEntries().size());

        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        String json = objectMapper.writeValueAsString(bundle);
        assertTrue(json.getBytes(StandardCharsets.UTF_8).length < 512 * 1024);
        JsonNode serialized = objectMapper.readTree(json);
        assertEquals(1, serialized.path("schemaVersion").asInt());
        assertEquals("ONGOING", serialized.path("defaultScene").asText());
        assertTrue(serialized.path("scenes").path("ongoing").path("stats").has("footprintAnalysis"));
        assertFalse(json.contains("shareToken"));
        assertFalse(json.contains("workflowId"));
    }

    @Test
    void build_rejectsMissingAssetConfiguration() {
        DemoJourneyProperties properties = properties();
        properties.setCosObjectKeyPrefix("");
        DemoJourneyFixtureFactory factory = factory(properties);

        IllegalStateException exception =
                assertThrows(IllegalStateException.class, () -> factory.build(REFERENCE_DATE, 0L));

        assertTrue(exception.getMessage().contains("COS object-key prefix is required"));
    }

    @Test
    void validator_rejectsAssetFromAnotherCosBucket() {
        DemoJourneyProperties properties = properties();
        DemoJourneyBundleResp bundle = factory(properties).build(REFERENCE_DATE, 0L);
        DemoJourneyCoverageValidator validator = new DemoJourneyCoverageValidator();

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> validator.validate(
                        bundle,
                        REFERENCE_DATE,
                        "other-bucket",
                        COS_REGION,
                        properties.getCosObjectKeyPrefix()));

        assertTrue(exception.getMessage().contains("configured COS bucket"));
    }

    @Test
    void validator_rejectsWritableCapability() {
        DemoJourneyProperties properties = properties();
        DemoJourneyBundleResp valid = factory(properties).build(REFERENCE_DATE, 0L);
        DemoJourneyCapabilitiesResp writable = new DemoJourneyCapabilitiesResp(
                true,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                true,
                false,
                false);
        DemoJourneyBundleResp invalid = new DemoJourneyBundleResp(
                valid.schemaVersion(),
                valid.contentVersion(),
                valid.referenceDate(),
                true,
                valid.defaultScene(),
                writable,
                valid.scenes());

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> new DemoJourneyCoverageValidator()
                        .validate(
                                invalid,
                                REFERENCE_DATE,
                                COS_BUCKET,
                                COS_REGION,
                                properties.getCosObjectKeyPrefix()));

        assertTrue(exception.getMessage().contains("cannot share"));
    }

    private DemoJourneyFixtureFactory factory(DemoJourneyProperties properties) {
        CosProperties cosProperties = cosProperties();
        return new DemoJourneyFixtureFactory(
                properties,
                new DemoJourneyCoverageValidator(),
                new MockCosClient(COS_BUCKET, COS_REGION),
                cosProperties,
                new CiUrlBuilder(new CiProperties()));
    }

    private DemoJourneyProperties properties() {
        DemoJourneyProperties properties = new DemoJourneyProperties();
        properties.setEnabled(true);
        properties.setSchemaVersion(1);
        properties.setContentVersion("2026.07.31.7");
        properties.setCosObjectKeyPrefix(COS_OBJECT_KEY_PREFIX);
        return properties;
    }

    private CosProperties cosProperties() {
        CosProperties properties = new CosProperties();
        properties.setEnabled(true);
        properties.setBucket(COS_BUCKET);
        properties.setRegion(COS_REGION);
        properties.setAccessUrlExpireSeconds(1800);
        return properties;
    }
}
