package com.tracci.module.demo.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tracci.common.context.LoginUserContext;
import com.tracci.common.exception.AuthException;
import com.tracci.integration.cos.CosProperties;
import com.tracci.integration.cos.MockCosClient;
import com.tracci.module.demo.config.DemoJourneyProperties;
import com.tracci.module.demo.dto.response.DemoJourneyBundleResp;
import com.tracci.module.demo.dto.response.DemoJourneyScenesResp;
import com.tracci.module.demo.fixture.DemoJourneyBundleProvider;
import com.tracci.module.demo.fixture.DemoJourneyFixtureFactory;
import com.tracci.module.demo.validation.DemoJourneyCoverageValidator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;


class DemoJourneyServiceImplTest {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-07-30T02:00:00Z"), ZoneOffset.UTC);

    @AfterEach
    void tearDown() {
        LoginUserContext.clear();
    }

    @Test
    void getJourney_returnsReadOnlyDisabledBundleWithoutScenes() throws Exception {
        DemoJourneyProperties properties = properties(false);
        DemoJourneyServiceImpl service = service(properties, List.of(), FIXED_CLOCK);
        LoginUserContext.setUserId("user-1");

        DemoJourneyDelivery delivery = service.getJourney(null);
        DemoJourneyBundleResp bundle = delivery.bundle();

        assertFalse(delivery.notModified());
        assertTrue(delivery.eTag().startsWith("W/\"demo-"));
        assertFalse(bundle.enabled());
        assertEquals(1, bundle.schemaVersion());
        assertEquals("2026.07.31.7", bundle.contentVersion());
        assertEquals("2026-07-30", bundle.referenceDate());
        assertEquals("ONGOING", bundle.defaultScene());
        assertNull(bundle.scenes());
        assertTrue(bundle.capabilities().readOnly());
        assertFalse(bundle.capabilities().canEditTrip());
        assertFalse(bundle.capabilities().canAddNode());
        assertFalse(bundle.capabilities().canEditJournal());
        assertFalse(bundle.capabilities().canManageExpense());
        assertFalse(bundle.capabilities().canManageMembers());
        assertFalse(bundle.capabilities().canInvite());
        assertFalse(bundle.capabilities().canDelete());
        assertFalse(bundle.capabilities().canShare());
        assertFalse(bundle.capabilities().canExport());
        assertFalse(bundle.capabilities().canApplyAiChanges());

        String json = new ObjectMapper().writeValueAsString(bundle);
        assertFalse(json.contains("\"scenes\""));
    }

    @Test
    void getJourney_requiresAuthenticatedUser() {
        DemoJourneyServiceImpl service = service(properties(false), List.of(), FIXED_CLOCK);

        assertThrows(AuthException.class, () -> service.getJourney(null));
    }

    @Test
    void getJourney_rejectsEnabledConfigurationUntilProviderIsRegistered() {
        DemoJourneyServiceImpl service = service(properties(true), List.of(), FIXED_CLOCK);
        LoginUserContext.setUserId("user-1");

        IllegalStateException exception =
                assertThrows(IllegalStateException.class, () -> service.getJourney(null));

        assertTrue(exception.getMessage().contains("exactly one typed bundle provider"));
    }

    @Test
    void getJourney_returnsValidatedBundleWhenEnabledProviderIsRegistered() {
        DemoJourneyProperties properties = properties(true);
        CosProperties cosProperties = cosProperties();
        DemoJourneyFixtureFactory factory =
                new DemoJourneyFixtureFactory(
                        properties,
                        new DemoJourneyCoverageValidator(),
                        new MockCosClient("test-bucket", "ap-guangzhou"),
                        cosProperties,
                        com.tracci.module.demo.contract.DemoJourneyContractSupport.ciUrlBuilder());
        DemoJourneyServiceImpl service = service(properties, List.of(factory), FIXED_CLOCK);
        LoginUserContext.setUserId("user-1");

        DemoJourneyBundleResp bundle = service.getJourney(null).bundle();

        assertTrue(bundle.enabled());
        assertEquals("ongoing", bundle.scenes().ongoing().trip().status());
        assertNotNull(bundle.scenes().ongoing().stats().footprintAnalysis());
    }

    @Test
    void getJourney_returnsNotModifiedWithoutBundleForCurrentETag() {
        DemoJourneyProperties properties = properties(false);
        DemoJourneyServiceImpl service = service(properties, List.of(), FIXED_CLOCK);
        LoginUserContext.setUserId("user-1");
        String eTag = service.getJourney(null).eTag();

        DemoJourneyDelivery delivery = service.getJourney(eTag);

        assertTrue(delivery.notModified());
        assertEquals(eTag, delivery.eTag());
        assertNull(delivery.bundle());
    }

    @Test
    void getJourney_usesWeakComparisonForStrongIfNoneMatchValue() {
        DemoJourneyServiceImpl service = service(properties(false), List.of(), FIXED_CLOCK);
        LoginUserContext.setUserId("user-1");
        String weakETag = service.getJourney(null).eTag();
        String strongETag = weakETag.substring(2);

        DemoJourneyDelivery delivery = service.getJourney(strongETag);

        assertTrue(delivery.notModified());
        assertEquals(weakETag, delivery.eTag());
    }

    @Test
    void getJourney_doesNotRebuildEnabledFixtureWhenETagMatches() {
        DemoJourneyProperties properties = properties(true);
        DemoJourneyBundleProvider provider = mock(DemoJourneyBundleProvider.class);
        DemoJourneyBundleResp bundle = new DemoJourneyBundleResp(
                1,
                "2026.07.31.7",
                "2026-07-30",
                true,
                DemoJourneyBundleResp.DEFAULT_SCENE,
                com.tracci.module.demo.dto.response.DemoJourneyCapabilitiesResp.allReadOnly(),
                mock(DemoJourneyScenesResp.class));
        when(provider.build(Mockito.<java.time.LocalDate>any(), Mockito.anyLong())).thenReturn(bundle);
        DemoJourneyServiceImpl service =
                service(properties, List.of(provider), FIXED_CLOCK);
        LoginUserContext.setUserId("user-1");
        String eTag = service.getJourney(null).eTag();
        clearInvocations(provider);

        DemoJourneyDelivery delivery = service.getJourney(eTag);

        assertTrue(delivery.notModified());
        verifyNoInteractions(provider);
    }

    @Test
    void getJourney_rotatesEnabledETagBeforeCosSignatureApproachesExpiry() {
        DemoJourneyProperties properties = properties(true);
        CosProperties cosProperties = cosProperties();
        DemoJourneyFixtureFactory factory =
                new DemoJourneyFixtureFactory(
                        properties,
                        new DemoJourneyCoverageValidator(),
                        new MockCosClient("test-bucket", "ap-guangzhou"),
                        cosProperties,
                        com.tracci.module.demo.contract.DemoJourneyContractSupport.ciUrlBuilder());
        DemoJourneyServiceImpl initial =
                new DemoJourneyServiceImpl(
                        properties,
                        List.of(factory),
                        cosProperties,
                        FIXED_CLOCK);
        DemoJourneyServiceImpl afterRotation =
                new DemoJourneyServiceImpl(
                        properties,
                        List.of(factory),
                        cosProperties,
                        Clock.offset(FIXED_CLOCK, java.time.Duration.ofSeconds(301)));
        LoginUserContext.setUserId("user-1");

        String initialETag = initial.getJourney(null).eTag();
        DemoJourneyDelivery refreshed = afterRotation.getJourney(initialETag);

        assertFalse(refreshed.notModified());
        assertNotNull(refreshed.bundle());
        assertFalse(initialETag.equals(refreshed.eTag()));
    }

    @Test
    void getJourney_rejectsUnsafeCosSignatureExpiryWhenEnabled() {
        DemoJourneyProperties properties = properties(true);
        CosProperties cosProperties = cosProperties();
        cosProperties.setAccessUrlExpireSeconds(60);
        DemoJourneyServiceImpl service =
                new DemoJourneyServiceImpl(
                        properties,
                        List.of((referenceDate, rotation) -> null),
                        cosProperties,
                        FIXED_CLOCK);
        LoginUserContext.setUserId("user-1");

        IllegalStateException exception =
                assertThrows(IllegalStateException.class, () -> service.getJourney(null));

        assertTrue(exception.getMessage().contains("at least 120 seconds"));
    }

    private DemoJourneyProperties properties(boolean enabled) {
        DemoJourneyProperties properties = new DemoJourneyProperties();
        properties.setEnabled(enabled);
        properties.setSchemaVersion(1);
        properties.setContentVersion("2026.07.31.7");
        properties.setCosObjectKeyPrefix("system/demo-journey/v1");
        return properties;
    }

    private CosProperties cosProperties() {
        CosProperties properties = new CosProperties();
        properties.setEnabled(true);
        properties.setBucket("test-bucket");
        properties.setRegion("ap-guangzhou");
        properties.setAccessUrlExpireSeconds(1800);
        return properties;
    }

    private DemoJourneyServiceImpl service(
            DemoJourneyProperties properties,
            List<DemoJourneyBundleProvider> providers,
            Clock clock) {
        return new DemoJourneyServiceImpl(properties, providers, cosProperties(), clock);
    }
}
