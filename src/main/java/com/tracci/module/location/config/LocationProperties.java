package com.tracci.module.location.config;

import com.tracci.module.location.provider.PlaceSearchProviderType;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties("tracci.location")
public class LocationProperties {

    private PlaceSearchProviderType defaultProvider = PlaceSearchProviderType.AMAP;
    private PlaceSearchProviderType iosProvider = PlaceSearchProviderType.DISABLED;
    private PlaceSearchProviderType wechatMiniProvider = PlaceSearchProviderType.AMAP;
    private boolean enableAmapForIos = false;
}
