package com.tracci.module.place.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.node.TextNode;
import com.tracci.common.exception.BizException;
import com.tracci.common.exception.NotFoundException;
import com.tracci.common.response.ErrorCode;
import com.tracci.integration.amap.AmapPoiClient;
import com.tracci.integration.amap.dto.AmapPoiDTO;
import com.tracci.module.place.entity.Place;
import com.tracci.module.place.entity.PlaceProviderRef;
import com.tracci.module.place.enums.PlaceStatus;
import com.tracci.module.place.repository.PlaceMapper;
import com.tracci.module.place.repository.PlaceProviderRefMapper;
import com.tracci.module.placecontent.dto.response.ContentMetaDto;
import com.tracci.module.placecontent.dto.response.EditorialContentDto;
import com.tracci.module.placecontent.dto.response.PublishedContentDto;
import com.tracci.module.placecontent.service.PlaceContentService;
import com.tracci.module.savedplace.dto.request.LocationCandidate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlaceServiceImplTest {

    @Mock
    private PlaceMapper placeMapper;

    @Mock
    private PlaceProviderRefMapper placeProviderRefMapper;

    @Mock
    private AmapPoiClient amapPoiClient;

    @Mock
    private PlaceContentService placeContentService;

    @InjectMocks
    private PlaceServiceImpl placeService;

    @Test
    void resolveOrCreatePlace_createsPlaceWithDestinationKeyForPoi() {
        when(placeProviderRefMapper.selectOne(any(QueryWrapper.class))).thenReturn(null);
        doAnswer(inv -> {
            Place p = inv.getArgument(0);
            p.setId("place-1");
            return 1;
        }).when(placeMapper).insert(any(Place.class));
        when(amapPoiClient.getDetail("B000A8UIN8")).thenReturn(amapPoi("B000A8UIN8", "浅草寺"));
        when(placeProviderRefMapper.insertIfAbsent(any(PlaceProviderRef.class))).thenReturn(1);

        LocationCandidate location = new LocationCandidate(
                "浅草寺",
                "东京都台东区",
                "AMAP",
                "B000A8UIN8",
                "日本",
                "东京",
                new BigDecimal("35.7148"),
                new BigDecimal("139.7967"),
                "131016",
                null
        );

        Place place = placeService.resolveOrCreatePlace(location, "POI", "ATTRACTION");

        assertEquals("place-1", place.getId());
        ArgumentCaptor<Place> captor = ArgumentCaptor.forClass(Place.class);
        verify(placeMapper).insert(captor.capture());
        assertEquals("POI", captor.getValue().getPlaceScope());
        assertEquals("adcode:131016", captor.getValue().getDestinationKey());
        verify(placeProviderRefMapper).insertIfAbsent(any(PlaceProviderRef.class));
    }

    @Test
    void resolveOrCreatePlace_reusesActivePlaceByProviderRef() {
        PlaceProviderRef ref = new PlaceProviderRef();
        ref.setPlaceId("existing-place");
        when(placeProviderRefMapper.selectOne(any(QueryWrapper.class))).thenReturn(ref);

        Place existing = new Place();
        existing.setId("existing-place");
        existing.setStatus(PlaceStatus.ACTIVE.name());
        existing.setPlaceScope("POI");
        existing.setCategory("ATTRACTION");
        existing.setCanonicalName("浅草寺");
        when(placeMapper.selectById("existing-place")).thenReturn(existing);

        LocationCandidate location = new LocationCandidate(
                "浅草寺", null, "AMAP", "B000A8UIN8",
                null, null, null, null, null, null);

        Place place = placeService.resolveOrCreatePlace(location, "POI", "OTHER");

        assertEquals("existing-place", place.getId());
        verify(placeMapper, never()).insert(any(Place.class));
    }

    @Test
    void resolveOrCreatePlace_rejectsNonActiveProviderIdentity() {
        PlaceProviderRef ref = new PlaceProviderRef();
        ref.setPlaceId("merged-place");
        when(placeProviderRefMapper.selectOne(any(QueryWrapper.class))).thenReturn(ref);

        Place merged = new Place();
        merged.setId("merged-place");
        merged.setStatus(PlaceStatus.MERGED.name());
        when(placeMapper.selectById("merged-place")).thenReturn(merged);

        LocationCandidate location = new LocationCandidate(
                "浅草寺", null, "AMAP", "B000A8UIN8",
                null, null, null, null, null, null);

        BizException ex = assertThrows(BizException.class,
                () -> placeService.resolveOrCreatePlace(location, "POI", "ATTRACTION"));
        assertEquals(ErrorCode.PLACE_IDENTITY_UNAVAILABLE, ex.getErrorCode());
        verify(placeMapper, never()).insert(any(Place.class));
    }

    @Test
    void resolveOrCreatePlace_handlesConcurrentProviderRefDuplicate() {
        when(placeProviderRefMapper.selectOne(any(QueryWrapper.class)))
                .thenReturn(null)
                .thenAnswer(inv -> {
                    PlaceProviderRef ref = new PlaceProviderRef();
                    ref.setPlaceId("winner-place");
                    return ref;
                });
        doAnswer(inv -> {
            Place p = inv.getArgument(0);
            p.setId("loser-place");
            return 1;
        }).when(placeMapper).insert(any(Place.class));
        when(amapPoiClient.getDetail("B000A8UIN8")).thenReturn(amapPoi("B000A8UIN8", "浅草寺"));
        when(placeProviderRefMapper.insertIfAbsent(any(PlaceProviderRef.class))).thenReturn(0);

        Place winner = new Place();
        winner.setId("winner-place");
        winner.setStatus(PlaceStatus.ACTIVE.name());
        winner.setPlaceScope("POI");
        winner.setCategory("ATTRACTION");
        when(placeMapper.selectById("winner-place")).thenReturn(winner);

        LocationCandidate location = new LocationCandidate(
                "浅草寺", null, "AMAP", "B000A8UIN8",
                null, null, null, null, null, null);

        Place place = placeService.resolveOrCreatePlace(location, "POI", "ATTRACTION");
        assertEquals("winner-place", place.getId());
        verify(placeMapper).deleteById("loser-place");
    }

    @Test
    void getPlaceById_rejectsDeprecated() {
        Place deprecated = new Place();
        deprecated.setId("p1");
        deprecated.setStatus(PlaceStatus.DEPRECATED.name());
        when(placeMapper.selectById("p1")).thenReturn(deprecated);

        NotFoundException ex = assertThrows(NotFoundException.class, () -> placeService.getPlaceById("p1"));
        assertEquals(ErrorCode.PLACE_NOT_FOUND, ex.getErrorCode());
    }

    @Test
    void validateScopeAndCategory_rejectsInvalidCombo() {
        BizException ex = assertThrows(BizException.class,
                () -> placeService.validateScopeAndCategory("DESTINATION", "ATTRACTION"));
        assertEquals(ErrorCode.PLACE_INVALID_SCOPE_CATEGORY, ex.getErrorCode());
    }

    @Test
    void resolveOrCreatePlace_destinationKeyNullWhenNoGeoHints() {
        doAnswer(inv -> {
            Place p = inv.getArgument(0);
            p.setId("manual-1");
            return 1;
        }).when(placeMapper).insert(any(Place.class));

        LocationCandidate location = new LocationCandidate(
                "自定义地点", null, null, null,
                null, null, null, null, null, null);

        placeService.resolveOrCreatePlace(location, "POI", "OTHER");
        ArgumentCaptor<Place> captor = ArgumentCaptor.forClass(Place.class);
        verify(placeMapper).insert(captor.capture());
        assertNull(captor.getValue().getDestinationKey());
        assertEquals("MANUAL", captor.getValue().getCreatedSource());
    }

    @Test
    void resolveOrCreatePlace_rejectsUnknownProvider() {
        LocationCandidate location = new LocationCandidate(
                "伪造地点", null, "UNKNOWN", "fake-id",
                null, null, null, null, null, null);

        BizException ex = assertThrows(BizException.class,
                () -> placeService.resolveOrCreatePlace(location, "POI", "OTHER"));

        assertEquals(ErrorCode.PLACE_IDENTITY_UNAVAILABLE, ex.getErrorCode());
        verify(placeMapper, never()).insert(any(Place.class));
    }

    @Test
    void resolveOrCreatePlace_usesVerifiedAmapSnapshot() {
        when(placeProviderRefMapper.selectOne(any(QueryWrapper.class))).thenReturn(null);
        when(amapPoiClient.getDetail("verified-id")).thenReturn(amapPoi("verified-id", "服务端名称"));
        when(placeProviderRefMapper.insertIfAbsent(any(PlaceProviderRef.class))).thenReturn(1);
        doAnswer(inv -> {
            Place p = inv.getArgument(0);
            p.setId("place-verified");
            return 1;
        }).when(placeMapper).insert(any(Place.class));

        LocationCandidate forged = new LocationCandidate(
                "客户端伪造名称", "客户端地址", "AMAP", "verified-id",
                "其他国家", "其他城市", null, null, null, null);

        Place place = placeService.resolveOrCreatePlace(forged, "POI", "OTHER");

        assertEquals("服务端名称", place.getCanonicalName());
        assertEquals("ATTRACTION", place.getCategory());
        assertEquals("东京", place.getCityName());
        assertEquals("adcode:131016", place.getDestinationKey());
    }

    @Test
    void getPlaceById_followsMergedIntoCanonical() {
        Place merged = new Place();
        merged.setId("merged-1");
        merged.setStatus(PlaceStatus.MERGED.name());
        merged.setMergedIntoId("canonical-1");
        Place canonical = new Place();
        canonical.setId("canonical-1");
        canonical.setStatus(PlaceStatus.ACTIVE.name());
        canonical.setCanonicalName("浅草寺");
        when(placeMapper.selectById("merged-1")).thenReturn(merged);
        when(placeMapper.selectById("canonical-1")).thenReturn(canonical);

        Place place = placeService.getPlaceById("merged-1");
        assertEquals("canonical-1", place.getId());
    }

    @Test
    void getPlaceDetail_usesCanonicalIdForPublishedContent() {
        Place place = new Place();
        place.setId("place-1");
        place.setStatus(PlaceStatus.ACTIVE.name());
        place.setPlaceScope("POI");
        place.setCategory("ATTRACTION");
        place.setCanonicalName("浅草寺");
        when(placeMapper.selectById("place-1")).thenReturn(place);

        EditorialContentDto editorial = new EditorialContentDto(
                1, "intro", List.of("h"), 60, null, null, null, null);
        ContentMetaDto meta = new ContentMetaDto(false, null, null, null, false);
        when(placeContentService.findPublishedContent("place-1"))
                .thenReturn(new PublishedContentDto(editorial, meta));
        when(placeContentService.findApprovedMedia("place-1")).thenReturn(List.of());

        var detail = placeService.getPlaceDetail("place-1");
        assertEquals("place-1", detail.id());
        assertEquals(1, detail.editorialContent().version());
        assertNull(detail.media());
    }

    private static AmapPoiDTO amapPoi(String id, String name) {
        return new AmapPoiDTO(
                id,
                name,
                TextNode.valueOf("东京都台东区"),
                "139.7967,35.7148",
                TextNode.valueOf("东京都"),
                TextNode.valueOf("东京"),
                TextNode.valueOf("台东区"),
                "131016",
                "03",
                "110000",
                "风景名胜"
        );
    }
}
