package com.tracci.module.location.service;

import com.tracci.integration.amap.AmapProperties;
import com.tracci.module.location.dto.request.LocationPoiSearchReq;
import com.tracci.module.location.provider.PlaceSearchPage;
import com.tracci.module.location.provider.PlaceSearchProvider;
import com.tracci.module.location.provider.PlaceSearchProviderRouter;
import com.tracci.module.location.provider.PlaceSearchProviderType;
import com.tracci.module.location.provider.PlaceSearchQuery;
import com.tracci.module.location.provider.PlaceSearchResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LocationPoiServiceImplTest {

    @Mock
    private PlaceSearchProviderRouter placeSearchProviderRouter;

    @Mock
    private PlaceSearchProvider amapProvider;

    private LocationPoiServiceImpl service;

    @BeforeEach
    void setUp() {
        AmapProperties properties = new AmapProperties();
        properties.getPoi().setDefaultPageSize(10);
        properties.getPoi().setMaxPageSize(20);
        service = new LocationPoiServiceImpl(placeSearchProviderRouter, properties);
    }

    @Test
    void search_defaultRequestUsesAmapProvider() {
        when(placeSearchProviderRouter.resolveProviderType(null)).thenReturn(PlaceSearchProviderType.AMAP);
        when(placeSearchProviderRouter.provider(PlaceSearchProviderType.AMAP)).thenReturn(amapProvider);
        when(amapProvider.search(any())).thenReturn(new PlaceSearchPage(List.of(
                new PlaceSearchResult(
                        "西湖风景名胜区",
                        "浙江省杭州市西湖区龙井路1号",
                        new BigDecimal("30.2420000"),
                        new BigDecimal("120.1410000"),
                        "杭州市",
                        null,
                        "AMAP",
                        "B023B",
                        Map.of("adcode", "330106", "cityCode", "0571", "provinceName", "浙江省", "districtName", "西湖区")
                )
        ), 1));

        var resp = service.search(new LocationPoiSearchReq(" 西湖 ", "杭州", null, null, 1, 10, null));

        assertEquals(1, resp.items().size());
        assertEquals("B023B", resp.items().getFirst().poiId());
        assertEquals(new BigDecimal("30.2420000"), resp.items().getFirst().latitude());
        assertEquals(new BigDecimal("120.1410000"), resp.items().getFirst().longitude());
        assertEquals("AMAP", resp.source());
        assertFalse(resp.hasMore());
    }

    @Test
    void search_iosRequestReturnsDisabledWithoutCallingProvider() {
        when(placeSearchProviderRouter.resolveProviderType("IOS")).thenReturn(PlaceSearchProviderType.DISABLED);

        var resp = service.search(new LocationPoiSearchReq("西湖", "杭州", null, null, 1, 10, "IOS"));

        assertEquals(List.of(), resp.items());
        assertEquals("DISABLED", resp.source());
        assertEquals("PROVIDER_DISABLED", resp.status());
        verify(amapProvider, never()).search(any());
    }

    @Test
    void search_wechatMiniUsesAmapProvider() {
        when(placeSearchProviderRouter.resolveProviderType("WECHAT_MINI")).thenReturn(PlaceSearchProviderType.AMAP);
        when(placeSearchProviderRouter.provider(PlaceSearchProviderType.AMAP)).thenReturn(amapProvider);
        when(amapProvider.search(any())).thenReturn(new PlaceSearchPage(List.of(), 0));

        service.search(new LocationPoiSearchReq("西湖", "杭州", null, null, 2, 5, "WECHAT_MINI"));

        ArgumentCaptor<PlaceSearchQuery> queryCaptor = ArgumentCaptor.forClass(PlaceSearchQuery.class);
        verify(amapProvider).search(queryCaptor.capture());
        assertEquals("西湖", queryCaptor.getValue().keyword());
        assertEquals("杭州", queryCaptor.getValue().city());
        assertEquals("WECHAT_MINI", queryCaptor.getValue().clientPlatform());
    }
}
