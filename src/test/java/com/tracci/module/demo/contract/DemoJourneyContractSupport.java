package com.tracci.module.demo.contract;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.tracci.integration.cos.CiProperties;
import com.tracci.integration.cos.CiUrlBuilder;
import com.tracci.integration.cos.CosProperties;
import com.tracci.integration.cos.MockCosClient;
import com.tracci.module.demo.config.DemoJourneyProperties;
import com.tracci.module.demo.dto.response.DemoJourneyBundleResp;
import com.tracci.module.demo.fixture.DemoJourneyFixtureFactory;
import com.tracci.module.demo.validation.DemoJourneyCoverageValidator;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.Set;
import java.util.TreeSet;

public final class DemoJourneyContractSupport {

    static final LocalDate REFERENCE_DATE = LocalDate.of(2026, 7, 30);
    static final String CONTENT_VERSION = "2026.07.31.7";
    static final String COS_BUCKET = "test-bucket";
    static final String COS_REGION = "ap-guangzhou";
    static final String COS_OBJECT_KEY_PREFIX = "system/demo-journey/v1";
    static final String SNAPSHOT_FILE_NAME = "demo-journey-v1.json";
    static final String METADATA_FILE_NAME = "demo-journey-v1.metadata.json";
    static final String ASSET_MANIFEST_FILE_NAME = "demo-journey-v1-assets.txt";

    private DemoJourneyContractSupport() {
    }

    static DemoJourneyBundleResp bundle() {
        DemoJourneyProperties demoProperties = new DemoJourneyProperties();
        demoProperties.setEnabled(true);
        demoProperties.setSchemaVersion(1);
        demoProperties.setContentVersion(CONTENT_VERSION);
        demoProperties.setCosObjectKeyPrefix(COS_OBJECT_KEY_PREFIX);

        CosProperties cosProperties = cosProperties();
        return new DemoJourneyFixtureFactory(
                demoProperties,
                new DemoJourneyCoverageValidator(),
                new MockCosClient(COS_BUCKET, COS_REGION),
                cosProperties,
                ciUrlBuilder())
                .build(REFERENCE_DATE, 0L);
    }

    public static CosProperties cosProperties() {
        CosProperties properties = new CosProperties();
        properties.setEnabled(true);
        properties.setBucket(COS_BUCKET);
        properties.setRegion(COS_REGION);
        properties.setAccessUrlExpireSeconds(1800);
        return properties;
    }

    public static CiUrlBuilder ciUrlBuilder() {
        return new CiUrlBuilder(new CiProperties());
    }

    static ObjectMapper objectMapper() {
        return JsonMapper.builder()
                .findAndAddModules()
                .serializationInclusion(JsonInclude.Include.NON_NULL)
                .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                .enable(SerializationFeature.INDENT_OUTPUT)
                .build();
    }

    static String snapshotJson() {
        try {
            return objectMapper()
                    .writerWithDefaultPrettyPrinter()
                    .writeValueAsString(bundle()) + "\n";
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to serialize Demo journey contract", exception);
        }
    }

    static String metadataJson(String snapshotJson) {
        ContractMetadata metadata = new ContractMetadata(
                1,
                CONTENT_VERSION,
                REFERENCE_DATE.toString(),
                SNAPSHOT_FILE_NAME,
                sha256(snapshotJson),
                snapshotJson.getBytes(StandardCharsets.UTF_8).length);
        try {
            return objectMapper()
                    .writerWithDefaultPrettyPrinter()
                    .writeValueAsString(metadata) + "\n";
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to serialize Demo journey metadata", exception);
        }
    }

    static String assetManifest(String snapshotJson) {
        try {
            JsonNode root = objectMapper().readTree(snapshotJson);
            Set<String> objectKeys = new TreeSet<>();
            collectCosObjectKeys(root, objectKeys);
            return String.join("\n", objectKeys) + "\n";
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to build Demo journey asset manifest", exception);
        }
    }

    private static void collectCosObjectKeys(JsonNode node, Set<String> objectKeys) {
        if (node.isTextual()) {
            String value = node.textValue();
            if (value.startsWith("https://" + COS_BUCKET + ".cos." + COS_REGION + ".myqcloud.com/")) {
                String path = URI.create(value).getPath();
                objectKeys.add(path.startsWith("/") ? path.substring(1) : path);
            }
            return;
        }
        node.elements().forEachRemaining(child -> collectCosObjectKeys(child, objectKeys));
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    record ContractMetadata(
            int schemaVersion,
            String contentVersion,
            String referenceDate,
            String snapshotFile,
            String sha256,
            int byteSize
    ) {
    }
}
