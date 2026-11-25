package com.tracci.module.location.provider;

import com.tracci.module.location.config.LocationProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlaceSearchProviderRouterTest {

    @Mock
    private AmapPlaceSearchProvider amapPlaceSearchProvider;

    @Mock
    private DisabledPlaceSearchProvider disabledPlaceSearchProvider;

    private LocationProperties properties;
    private PlaceSearchProviderRouter router;

    @BeforeEach
    void setUp() {
        properties = new LocationProperties();
        router = new PlaceSearchProviderRouter(properties, amapPlaceSearchProvider, disabledPlaceSearchProvider);
    }

    @Test
    void resolveProviderType_defaultsToAmapWhenClientPlatformMissing() {
        assertEquals(PlaceSearchProviderType.AMAP, router.resolveProviderType(null));
    }

    @Test
    void resolveProviderType_wechatMiniUsesAmap() {
        assertEquals(PlaceSearchProviderType.AMAP, router.resolveProviderType("WECHAT_MINI"));
    }

    @Test
    void resolveProviderType_iosUsesDisabledByDefault() {
        assertEquals(PlaceSearchProviderType.DISABLED, router.resolveProviderType("IOS"));
    }

    @Test
    void resolveProviderType_iosCanEnableAmapViaConfig() {
        properties.setEnableAmapForIos(true);
        assertEquals(PlaceSearchProviderType.AMAP, router.resolveProviderType("IOS"));
    }

    @Test
    void provider_returnsMatchingImplementation() {
        assertEquals(amapPlaceSearchProvider, router.provider(PlaceSearchProviderType.AMAP));
        assertEquals(disabledPlaceSearchProvider, router.provider(PlaceSearchProviderType.DISABLED));
    }
}
