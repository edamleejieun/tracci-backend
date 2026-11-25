package com.tracci.module.location.provider;

import com.tracci.common.enums.ClientPlatform;
import com.tracci.module.location.config.LocationProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PlaceSearchProviderRouter {

    private final LocationProperties locationProperties;
    private final AmapPlaceSearchProvider amapPlaceSearchProvider;
    private final DisabledPlaceSearchProvider disabledPlaceSearchProvider;

    public PlaceSearchProviderType resolveProviderType(String clientPlatform) {
        ClientPlatform platform = ClientPlatform.from(clientPlatform);
        if (platform == ClientPlatform.UNKNOWN) {
            return locationProperties.getDefaultProvider();
        }
        return switch (platform) {
            case WECHAT_MINI -> locationProperties.getWechatMiniProvider();
            case IOS -> resolveIosProvider();
            case H5 -> locationProperties.getDefaultProvider();
            default -> locationProperties.getDefaultProvider();
        };
    }

    public PlaceSearchProvider resolve(String clientPlatform) {
        return provider(resolveProviderType(clientPlatform));
    }

    public PlaceSearchProvider provider(PlaceSearchProviderType type) {
        if (type == null) {
            return disabledPlaceSearchProvider;
        }
        return switch (type) {
            case AMAP -> amapPlaceSearchProvider;
            case DISABLED, APPLE, MANUAL -> disabledPlaceSearchProvider;
        };
    }

    private PlaceSearchProviderType resolveIosProvider() {
        if (locationProperties.isEnableAmapForIos()) {
            return PlaceSearchProviderType.AMAP;
        }
        return locationProperties.getIosProvider();
    }
}
