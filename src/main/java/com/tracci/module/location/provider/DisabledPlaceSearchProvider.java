package com.tracci.module.location.provider;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DisabledPlaceSearchProvider implements PlaceSearchProvider {

    @Override
    public PlaceSearchProviderType providerType() {
        return PlaceSearchProviderType.DISABLED;
    }

    @Override
    public PlaceSearchPage search(PlaceSearchQuery query) {
        return new PlaceSearchPage(List.of(), 0);
    }
}
