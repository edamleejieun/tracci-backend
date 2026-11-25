package com.tracci.module.demo.contract;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class DemoJourneyContractGenerator {

    private DemoJourneyContractGenerator() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("Expected one output directory argument");
        }
        Path outputDirectory = Path.of(args[0]).toAbsolutePath().normalize();
        Files.createDirectories(outputDirectory);

        String snapshot = DemoJourneyContractSupport.snapshotJson();
        Files.writeString(
                outputDirectory.resolve(DemoJourneyContractSupport.SNAPSHOT_FILE_NAME),
                snapshot,
                StandardCharsets.UTF_8);
        Files.writeString(
                outputDirectory.resolve(DemoJourneyContractSupport.METADATA_FILE_NAME),
                DemoJourneyContractSupport.metadataJson(snapshot),
                StandardCharsets.UTF_8);
        Files.writeString(
                outputDirectory.resolve(DemoJourneyContractSupport.ASSET_MANIFEST_FILE_NAME),
                DemoJourneyContractSupport.assetManifest(snapshot),
                StandardCharsets.UTF_8);
    }
}
