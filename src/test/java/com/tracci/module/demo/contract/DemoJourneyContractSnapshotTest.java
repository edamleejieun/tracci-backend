package com.tracci.module.demo.contract;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DemoJourneyContractSnapshotTest {

    @Test
    void generatedContract_matchesCommittedGoldenFiles() throws Exception {
        String generatedSnapshot = DemoJourneyContractSupport.snapshotJson();

        assertEquals(resource(DemoJourneyContractSupport.SNAPSHOT_FILE_NAME), generatedSnapshot);
        assertEquals(
                resource(DemoJourneyContractSupport.METADATA_FILE_NAME),
                DemoJourneyContractSupport.metadataJson(generatedSnapshot));
        assertEquals(
                resource(DemoJourneyContractSupport.ASSET_MANIFEST_FILE_NAME),
                DemoJourneyContractSupport.assetManifest(generatedSnapshot));
    }

    @Test
    void committedContract_isClientSafeAndWithinBudget() throws Exception {
        String snapshot = resource(DemoJourneyContractSupport.SNAPSHOT_FILE_NAME);
        JsonNode root = DemoJourneyContractSupport.objectMapper().readTree(snapshot);

        assertEquals(1, root.path("schemaVersion").asInt());
        assertEquals(DemoJourneyContractSupport.CONTENT_VERSION, root.path("contentVersion").asText());
        assertEquals("ONGOING", root.path("defaultScene").asText());
        assertTrue(root.path("capabilities").path("readOnly").asBoolean());
        assertTrue(root.path("scenes").has("planning"));
        assertTrue(root.path("scenes").has("ongoing"));
        assertTrue(root.path("scenes").has("completed"));
        assertTrue(snapshot.getBytes(StandardCharsets.UTF_8).length < 512 * 1024);
        assertFalse(snapshot.contains("shareToken"));
        assertFalse(snapshot.contains("workflowId"));
        assertFalse(snapshot.contains("/users/"));

        String assetManifest = resource(DemoJourneyContractSupport.ASSET_MANIFEST_FILE_NAME);
        assertEquals(13L, assetManifest.lines().count());
        assertTrue(assetManifest.contains("/photos/the-bund.png"));
        assertTrue(assetManifest.contains("/photos/disney-castle.png"));
        assertTrue(assetManifest.contains("/photos/lujiazui-skyline.png"));
        assertTrue(assetManifest.contains("/cover/hero.png"));
        assertTrue(assetManifest.lines().allMatch(path -> path.endsWith(".png")));
        assertFalse(assetManifest.contains("/thumbnail/"));
        assertFalse(assetManifest.contains("/preview/"));
        assertFalse(assetManifest.contains("/export/"));
    }

    private String resource(String fileName) throws Exception {
        try (var input = getClass().getResourceAsStream("/contracts/" + fileName)) {
            if (input == null) {
                throw new IllegalStateException("Missing committed contract resource: " + fileName);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
