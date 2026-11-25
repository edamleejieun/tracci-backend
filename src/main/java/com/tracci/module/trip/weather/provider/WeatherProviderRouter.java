package com.tracci.module.trip.weather.provider;

import com.tracci.common.enums.ClientPlatform;
import com.tracci.module.trip.weather.config.WeatherProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class WeatherProviderRouter {

    private final WeatherProperties weatherProperties;
    private final AmapWeatherProvider amapWeatherProvider;
    private final AppleClientWeatherProvider appleClientWeatherProvider;
    private final DisabledWeatherProvider disabledWeatherProvider;

    public WeatherProviderType resolveProviderType(String clientPlatform) {
        ClientPlatform platform = ClientPlatform.from(clientPlatform);
        if (platform == ClientPlatform.UNKNOWN) {
            return weatherProperties.getDefaultProvider();
        }
        return switch (platform) {
            case WECHAT_MINI -> weatherProperties.getWechatMiniProvider();
            case IOS -> resolveIosProvider();
            case H5 -> weatherProperties.getDefaultProvider();
            default -> weatherProperties.getDefaultProvider();
        };
    }

    public TripWeatherProvider resolve(String clientPlatform) {
        return provider(resolveProviderType(clientPlatform));
    }

    public TripWeatherProvider provider(WeatherProviderType type) {
        if (type == null) {
            return disabledWeatherProvider;
        }
        return switch (type) {
            case AMAP -> amapWeatherProvider;
            case APPLE_CLIENT -> appleClientWeatherProvider;
            case DISABLED -> disabledWeatherProvider;
        };
    }

    private WeatherProviderType resolveIosProvider() {
        if (weatherProperties.isEnableAmapForIos()) {
            return WeatherProviderType.AMAP;
        }
        return weatherProperties.getIosProvider();
    }
}
