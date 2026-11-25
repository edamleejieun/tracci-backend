package com.tracci.module.demo.service;

import com.tracci.common.context.LoginUserContext;
import com.tracci.common.util.DateTimeUtil;
import com.tracci.common.util.HashUtil;
import com.tracci.integration.cos.CosProperties;
import com.tracci.module.demo.config.DemoJourneyProperties;
import com.tracci.module.demo.dto.response.DemoJourneyBundleResp;
import com.tracci.module.demo.fixture.DemoJourneyBundleProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Service
public class DemoJourneyServiceImpl implements DemoJourneyService {

    private final DemoJourneyProperties properties;
    private final List<DemoJourneyBundleProvider> bundleProviders;
    private final CosProperties cosProperties;
    private final Clock clock;

    @Autowired
    DemoJourneyServiceImpl(
            DemoJourneyProperties properties,
            List<DemoJourneyBundleProvider> bundleProviders,
            CosProperties cosProperties) {
        this(
                properties,
                bundleProviders,
                cosProperties,
                Clock.system(DateTimeUtil.BUSINESS_ZONE));
    }

    DemoJourneyServiceImpl(
            DemoJourneyProperties properties,
            List<DemoJourneyBundleProvider> bundleProviders,
            CosProperties cosProperties,
            Clock clock) {
        this.properties = properties;
        this.bundleProviders = List.copyOf(bundleProviders);
        this.cosProperties = cosProperties;
        this.clock = clock;
    }

    @Override
    public DemoJourneyDelivery getJourney(String ifNoneMatch) {
        LoginUserContext.requireUserId();
        LocalDate referenceDate = LocalDate.now(clock.withZone(DateTimeUtil.BUSINESS_ZONE));
        if (properties.isEnabled() && bundleProviders.size() != 1) {
            throw new IllegalStateException(
                    "Demo journey is enabled but exactly one typed bundle provider is required");
        }
        String eTag = buildETag(referenceDate);
        if (matches(ifNoneMatch, eTag)) {
            return DemoJourneyDelivery.notModified(eTag);
        }

        if (!properties.isEnabled()) {
            return DemoJourneyDelivery.body(
                    eTag,
                    DemoJourneyBundleResp.disabled(
                            properties.getSchemaVersion(),
                            properties.getContentVersion(),
                            referenceDate.toString()));
        }

        long signatureRotation = currentSignatureRotation();
        DemoJourneyBundleResp bundle = bundleProviders.getFirst().build(referenceDate, signatureRotation);
        if (bundle == null || !bundle.enabled() || bundle.scenes() == null) {
            throw new IllegalStateException(
                    "Demo journey provider must return an enabled bundle with complete scenes");
        }
        return DemoJourneyDelivery.body(eTag, bundle);
    }

    private String buildETag(LocalDate referenceDate) {
        StringBuilder identity = new StringBuilder()
                .append(properties.getSchemaVersion()).append('\n')
                .append(properties.getContentVersion()).append('\n')
                .append(referenceDate).append('\n')
                .append(properties.isEnabled());
        if (properties.isEnabled()) {
            identity.append('\n').append(cosProperties.getBucket())
                    .append('\n').append(cosProperties.getRegion())
                    .append('\n').append(properties.getCosObjectKeyPrefix())
                    .append('\n').append(cosProperties.getAccessUrlExpireSeconds())
                    .append('\n').append(currentSignatureRotation());
        }
        return "W/\"demo-" + HashUtil.sha256Hex(identity.toString()).substring(0, 24) + "\"";
    }

    private long currentSignatureRotation() {
        int expireSeconds = cosProperties.getAccessUrlExpireSeconds();
        if (expireSeconds < 120) {
            throw new IllegalStateException(
                    "Demo journey COS access URL expiry must be at least 120 seconds");
        }
        long rotationSeconds = Math.min(300L, expireSeconds / 2L);
        return Instant.now(clock).getEpochSecond() / rotationSeconds;
    }

    private boolean matches(String ifNoneMatch, String currentETag) {
        if (ifNoneMatch == null || ifNoneMatch.isBlank()) {
            return false;
        }
        for (String candidate : ifNoneMatch.split(",")) {
            String trimmed = candidate.trim();
            if ("*".equals(trimmed) || weakETagValue(currentETag).equals(weakETagValue(trimmed))) {
                return true;
            }
        }
        return false;
    }

    private String weakETagValue(String eTag) {
        return eTag.startsWith("W/") ? eTag.substring(2) : eTag;
    }
}
