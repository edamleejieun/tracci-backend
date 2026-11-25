package com.tracci.module.trip.weather.provider;

import com.tracci.module.trip.weather.config.WeatherProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WeatherProviderRouterTest {

    @Mock
    private AmapWeatherProvider amapWeatherProvider;

    @Mock
    private AppleClientWeatherProvider appleClientWeatherProvider;

    @Mock
    private DisabledWeatherProvider disabledWeatherProvider;

    private WeatherProperties properties;
    private WeatherProviderRouter router;

    @BeforeEach
    void setUp() {
        properties = new WeatherProperties();
        router = new WeatherProviderRouter(
                properties,
                amapWeatherProvider,
                appleClientWeatherProvider,
                disabledWeatherProvider);
    }

    @Test
    void resolveProviderType_defaultsToAmapWhenClientPlatformMissing() {
        assertEquals(WeatherProviderType.AMAP, router.resolveProviderType(null));
    }

    @Test
    void resolveProviderType_wechatMiniUsesAmap() {
        assertEquals(WeatherProviderType.AMAP, router.resolveProviderType("WECHAT_MINI"));
    }

    @Test
    void resolveProviderType_iosUsesAppleClientByDefault() {
        assertEquals(WeatherProviderType.APPLE_CLIENT, router.resolveProviderType("IOS"));
    }

    @Test
    void resolveProviderType_iosCanEnableAmapViaConfig() {
        properties.setEnableAmapForIos(true);
        assertEquals(WeatherProviderType.AMAP, router.resolveProviderType("IOS"));
    }

    @Test
    void provider_returnsMatchingImplementation() {
        assertEquals(amapWeatherProvider, router.provider(WeatherProviderType.AMAP));
        assertEquals(appleClientWeatherProvider, router.provider(WeatherProviderType.APPLE_CLIENT));
        assertEquals(disabledWeatherProvider, router.provider(WeatherProviderType.DISABLED));
    }
}
