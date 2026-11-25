package com.tracci.module.location.provider;

public interface PlaceSearchProvider {

    PlaceSearchProviderType providerType();

    PlaceSearchPage search(PlaceSearchQuery query);
}
