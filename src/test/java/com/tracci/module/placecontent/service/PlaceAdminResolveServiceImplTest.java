package com.tracci.module.placecontent.service;

import com.tracci.common.exception.BizException;
import com.tracci.common.response.ErrorCode;
import com.tracci.common.response.PageResponse;
import com.tracci.module.location.dto.request.LocationPoiSearchReq;
import com.tracci.module.location.dto.response.LocationPoiDTO;
import com.tracci.module.location.dto.response.LocationPoiSearchResp;
import com.tracci.module.location.service.LocationPoiService;
import com.tracci.module.place.entity.Place;
import com.tracci.module.place.enums.PlaceStatus;
import com.tracci.module.place.repository.PlaceMapper;
import com.tracci.module.place.service.PlaceService;
import com.tracci.module.placecontent.dto.request.PlaceAdminCreateRequest;
import com.tracci.module.placecontent.dto.request.PlaceAdminSearchQuery;
import com.tracci.module.placecontent.dto.request.PlaceAdminUpdateRequest;
import com.tracci.module.placecontent.dto.request.PlaceResolveFromPoiRequest;
import com.tracci.module.placecontent.dto.response.PlaceAdminSearchItem;
import com.tracci.module.savedplace.dto.request.LocationCandidate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 管理端 POI 候选与受信解析测试：
 * 高德代理参数、AMAP 候选构造、解析后经搜索组装返回条目。
 */
@ExtendWith(MockitoExtension.class)
class PlaceAdminResolveServiceImplTest {

    @Mock
    private PlaceService placeService;

    @Mock
    private LocationPoiService locationPoiService;

    @Mock
    private PlaceContentService placeContentService;

    @Mock
    private PlaceMapper placeMapper;

    private PlaceAdminResolveServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PlaceAdminResolveServiceImpl(
                placeService, locationPoiService, placeContentService, placeMapper);
    }

    @Test
    void poiSuggestions_delegatesToAmapSearchWithAdminPlatform() {
        LocationPoiDTO poi = new LocationPoiDTO(
                "B000A816R6", "故宫博物院", "北京市东城区景山前街4号",
                new BigDecimal("39.9163"), new BigDecimal("116.3972"),
                "北京市", "北京市", "东城区", "110101", "010",
                "110200", "风景名胜", "AMAP");
        when(locationPoiService.search(any(LocationPoiSearchReq.class)))
                .thenReturn(new LocationPoiSearchResp(List.of(poi), 1, 10, false, "AMAP"));

        List<LocationPoiDTO> result = service.listPoiSuggestions("  故宫  ", "北京");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).poiId()).isEqualTo("B000A816R6");
        ArgumentCaptor<LocationPoiSearchReq> captor = ArgumentCaptor.forClass(LocationPoiSearchReq.class);
        verify(locationPoiService).search(captor.capture());
        assertThat(captor.getValue().keyword()).isEqualTo("故宫");
        assertThat(captor.getValue().city()).isEqualTo("北京");
        assertThat(captor.getValue().size()).isEqualTo(10);
    }

    @Test
    void poiSuggestions_blankOrShortKeyword_isRejected() {
        assertThatThrownBy(() -> service.listPoiSuggestions("  ", null))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getErrorCode())
                .isEqualTo(ErrorCode.PARAM_INVALID);
        assertThatThrownBy(() -> service.listPoiSuggestions("故", null))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getErrorCode())
                .isEqualTo(ErrorCode.PARAM_INVALID);
    }

    @Test
    void resolvePlaceFromPoi_buildsAmapCandidateAndAssemblesItemViaSearch() {
        Place resolved = new Place();
        resolved.setId("place-100");
        resolved.setCanonicalName("故宫博物院");
        resolved.setCityName("北京市");
        resolved.setPlaceScope("POI");
        resolved.setCategory("ATTRACTION");
        resolved.setStatus("ACTIVE");
        when(placeService.resolveOrCreatePlace(any(LocationCandidate.class), eq("POI"), eq("ATTRACTION")))
                .thenReturn(resolved);
        PlaceAdminSearchItem assembled = new PlaceAdminSearchItem(
                "place-100", "故宫博物院", "POI", "ATTRACTION", "AMAP", "ACTIVE", null,
                "北京市东城区景山前街4号", "北京市", "中国", null, null,
                List.of(), false, null, null, 0);
        when(placeContentService.searchPlacesForAdmin(any(PlaceAdminSearchQuery.class)))
                .thenReturn(PageResponse.of(List.of(assembled), 1, 1, 1));

        PlaceAdminSearchItem item = service.resolvePlaceFromPoi(new PlaceResolveFromPoiRequest(
                "B000A816R6", "故宫博物院", "北京市东城区景山前街4号", "北京市",
                new BigDecimal("39.9163"), new BigDecimal("116.3972"), "110101", "010", null));

        assertThat(item.id()).isEqualTo("place-100");

        ArgumentCaptor<LocationCandidate> candidateCaptor = ArgumentCaptor.forClass(LocationCandidate.class);
        verify(placeService).resolveOrCreatePlace(candidateCaptor.capture(), eq("POI"), eq("ATTRACTION"));
        LocationCandidate candidate = candidateCaptor.getValue();
        assertThat(candidate.provider()).isEqualTo("AMAP");
        assertThat(candidate.providerPlaceId()).isEqualTo("B000A816R6");
        assertThat(candidate.name()).isEqualTo("故宫博物院");
        assertThat(candidate.cityName()).isEqualTo("北京市");

        ArgumentCaptor<PlaceAdminSearchQuery> queryCaptor = ArgumentCaptor.forClass(PlaceAdminSearchQuery.class);
        verify(placeContentService).searchPlacesForAdmin(queryCaptor.capture());
        assertThat(queryCaptor.getValue().placeId()).isEqualTo("place-100");
    }

    @Test
    void createPlace_reusesActiveDestinationWithSameName() {
        Place existing = destination("city-1", "京都");
        when(placeMapper.selectOne(any())).thenReturn(existing);
        when(placeContentService.searchPlacesForAdmin(any(PlaceAdminSearchQuery.class)))
                .thenReturn(PageResponse.of(List.of(assembled("city-1", "京都", "DESTINATION")), 1, 1, 1));

        PlaceAdminSearchItem result = service.createPlace(new PlaceAdminCreateRequest(
                "DESTINATION", "CITY", "京都", "日本", null, null, null, null, null));

        assertThat(result.id()).isEqualTo("city-1");
        verify(placeService, never()).resolveOrCreatePlace(any(), any(), any());
    }

    @Test
    void createPlace_poiRequiresDestination() {
        assertThatThrownBy(() -> service.createPlace(new PlaceAdminCreateRequest(
                "POI", "ATTRACTION", "清水寺", "日本", null, null, null, null, null)))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.PARAM_INVALID);
    }

    @Test
    void createPlace_poiStampsCityNameFromDestination() {
        when(placeService.getPlaceById("city-1")).thenReturn(destination("city-1", "京都"));
        Place created = new Place();
        created.setId("poi-1");
        created.setCityName("京都市");
        when(placeService.resolveOrCreatePlace(any(), eq("POI"), eq("ATTRACTION"))).thenReturn(created);
        when(placeContentService.searchPlacesForAdmin(any(PlaceAdminSearchQuery.class)))
                .thenReturn(PageResponse.of(List.of(assembled("poi-1", "清水寺", "POI")), 1, 1, 1));

        service.createPlace(new PlaceAdminCreateRequest(
                "POI", "ATTRACTION", "清水寺", null, null, "city-1", null, null, null));

        ArgumentCaptor<LocationCandidate> captor = ArgumentCaptor.forClass(LocationCandidate.class);
        verify(placeService).resolveOrCreatePlace(captor.capture(), eq("POI"), eq("ATTRACTION"));
        assertThat(captor.getValue().cityName()).isEqualTo("京都");
        verify(placeMapper).update(isNull(), any());
    }

    @Test
    void createPlace_sameNameDifferentCountry_createsANewDestination() {
        when(placeMapper.selectOne(any())).thenReturn(null);
        Place created = destination("city-2", "京都");
        created.setCountry("中国");
        when(placeService.resolveOrCreatePlace(any(), eq("DESTINATION"), eq("CITY"))).thenReturn(created);
        when(placeContentService.searchPlacesForAdmin(any(PlaceAdminSearchQuery.class)))
                .thenReturn(PageResponse.of(List.of(assembled("city-2", "京都", "DESTINATION")), 1, 1, 1));

        PlaceAdminSearchItem result = service.createPlace(new PlaceAdminCreateRequest(
                "DESTINATION", "CITY", "京都", "中国", null, null, null, null, null));

        assertThat(result.id()).isEqualTo("city-2");
        verify(placeService).resolveOrCreatePlace(any(), eq("DESTINATION"), eq("CITY"));
    }

    @Test
    void updatePlace_doesNotCascadeCityRenameUnlessExplicit() {
        when(placeService.getPlaceById("city-1")).thenReturn(destination("city-1", "京都"));
        when(placeContentService.searchPlacesForAdmin(any(PlaceAdminSearchQuery.class)))
                .thenReturn(PageResponse.of(List.of(assembled("city-1", "京都市", "DESTINATION")), 1, 1, 1));

        service.updatePlace("city-1", new PlaceAdminUpdateRequest(
                "京都市", null, null, null, null, null, null, null));

        verify(placeMapper, times(1)).update(isNull(), any());
    }

    @Test
    void updatePlace_cascadesCityRenameWhenRequested() {
        when(placeService.getPlaceById("city-1")).thenReturn(destination("city-1", "京都"));
        when(placeContentService.searchPlacesForAdmin(any(PlaceAdminSearchQuery.class)))
                .thenReturn(PageResponse.of(List.of(assembled("city-1", "京都市", "DESTINATION")), 1, 1, 1));

        service.updatePlace("city-1", new PlaceAdminUpdateRequest(
                "京都市", null, null, null, null, null, null, true));

        verify(placeMapper, times(2)).update(isNull(), any());
    }

    private static Place destination(String id, String name) {
        Place place = new Place();
        place.setId(id);
        place.setCanonicalName(name);
        place.setPlaceScope("DESTINATION");
        place.setCategory("CITY");
        place.setCountry("日本");
        place.setStatus(PlaceStatus.ACTIVE.name());
        return place;
    }

    private static PlaceAdminSearchItem assembled(String id, String name, String scope) {
        return new PlaceAdminSearchItem(
                id, name, scope, "CITY", "MANUAL", "ACTIVE", null,
                null, name, "日本", null, null, List.of(), false, null, null, 0);
    }
}
