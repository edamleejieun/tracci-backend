package com.tracci.module.demo.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record DemoJourneyBundleResp(
        int schemaVersion,
        String contentVersion,
        String referenceDate,
        boolean enabled,
        String defaultScene,
        DemoJourneyCapabilitiesResp capabilities,
        DemoJourneyScenesResp scenes
) {

    public static final String DEFAULT_SCENE = "ONGOING";

    public static DemoJourneyBundleResp disabled(
            int schemaVersion,
            String contentVersion,
            String referenceDate) {
        return new DemoJourneyBundleResp(
                schemaVersion,
                contentVersion,
                referenceDate,
                false,
                DEFAULT_SCENE,
                DemoJourneyCapabilitiesResp.allReadOnly(),
                null);
    }
}
