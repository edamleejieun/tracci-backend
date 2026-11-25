package com.tracci.module.trip.weather.config;

import com.tracci.module.trip.weather.provider.WeatherProviderType;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties("tracci.weather")
public class WeatherProperties {

    private WeatherProviderType defaultProvider = WeatherProviderType.AMAP;
    private WeatherProviderType iosProvider = WeatherProviderType.APPLE_CLIENT;
    private WeatherProviderType wechatMiniProvider = WeatherProviderType.AMAP;
    private boolean enableAmapForIos = false;
}
