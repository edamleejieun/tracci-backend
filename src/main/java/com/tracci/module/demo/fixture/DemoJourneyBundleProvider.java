package com.tracci.module.demo.fixture;

import com.tracci.module.demo.dto.response.DemoJourneyBundleResp;

import java.time.LocalDate;

/**
 * P2 extension point for the typed fixture factory.
 */
public interface DemoJourneyBundleProvider {

    DemoJourneyBundleResp build(LocalDate referenceDate, long signatureRotation);
}
