package com.tracci.module.demo.service;

import com.tracci.module.demo.dto.response.DemoJourneyBundleResp;

public record DemoJourneyDelivery(
        String eTag,
        boolean notModified,
        DemoJourneyBundleResp bundle
) {

    public static DemoJourneyDelivery notModified(String eTag) {
        return new DemoJourneyDelivery(eTag, true, null);
    }

    public static DemoJourneyDelivery body(String eTag, DemoJourneyBundleResp bundle) {
        return new DemoJourneyDelivery(eTag, false, bundle);
    }
}
