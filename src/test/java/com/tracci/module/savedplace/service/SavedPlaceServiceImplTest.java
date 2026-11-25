package com.tracci.module.savedplace.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tracci.common.exception.BizException;
import com.tracci.common.exception.NotFoundException;
import com.tracci.common.response.ErrorCode;
import com.tracci.integration.wechat.WechatContentSecurityClient;
import com.tracci.module.auth.repository.UserAuthIdentityMapper;
import com.tracci.module.place.entity.Place;
import com.tracci.module.place.enums.PlaceScope;
import com.tracci.module.place.service.PlaceService;
import com.tracci.module.savedplace.dto.request.CreateSavedPlaceRequest;
import com.tracci.module.savedplace.dto.request.LocationCandidate;
import com.tracci.module.savedplace.dto.request.UpdateSavedPlaceRequest;
import com.tracci.module.savedplace.entity.SavedPlace;
import com.tracci.module.savedplace.entity.SavedPlaceUsage;
import com.tracci.module.savedplace.repository.SavedPlaceMapper;
import com.tracci.module.savedplace.repository.SavedPlaceUsageMapper;
import com.tracci.module.trip.entity.Trip;
import com.tracci.module.trip.entity.TripMember;
import com.tracci.module.trip.enums.TripMemberRole;
import com.tracci.module.trip.enums.TripPermission;
import com.tracci.module.trip.service.TripAccessService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SavedPlaceServiceImplTest {

    @Mock
    private SavedPlaceMapper savedPlaceMapper;

    @Mock
    private SavedPlaceUsageMapper savedPlaceUsageMapper;

    @Mock
    private PlaceService placeService;

    @Mock
    private TripAccessService tripAccessService;

    @Mock
    private UserAuthIdentityMapper authIdentityMapper;

    @Mock
    private WechatContentSecurityClient wechatContentSecurityClient;

    @InjectMocks
    private SavedPlaceServiceImpl savedPlaceService;

    @Test
    void createSavedPlace_success() {
        LocationCandidate location = location("浅草寺", "131016");
        Place place = activePoi("place-1", "adcode:131016");
        when(placeService.resolveOrCreatePlace(location, "POI", "ATTRACTION")).thenReturn(place);
        when(savedPlaceMapper.selectOne(any(QueryWrapper.class))).thenReturn(null);
        doAnswer(inv -> {
            SavedPlace sp = inv.getArgument(0);
            sp.setId("sp-1");
            sp.setCreatedAt(LocalDateTime.of(2026, 7, 1, 0, 0));
            sp.setUpdatedAt(LocalDateTime.of(2026, 7, 1, 0, 0));
            return 1;
        }).when(savedPlaceMapper).insert(any(SavedPlace.class));
        when(savedPlaceUsageMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of());

        var resp = savedPlaceService.createSavedPlace("u-1",
                new CreateSavedPlaceRequest(null, "POI", "ATTRACTION", location, "想去"));

        assertEquals("sp-1", resp.id());
        assertEquals("place-1", resp.placeId());
        assertEquals("浅草寺", resp.place().name());
        verify(placeService).validateScopeAndCategory("POI", "ATTRACTION");
    }

    @Test
    void createSavedPlace_rejectsDuplicate() {
        LocationCandidate location = location("浅草寺", null);
        Place place = activePoi("place-1", null);
        when(placeService.resolveOrCreatePlace(location, "POI", "ATTRACTION")).thenReturn(place);
        SavedPlace existing = new SavedPlace();
        existing.setId("sp-old");
        when(savedPlaceMapper.selectOne(any(QueryWrapper.class))).thenReturn(existing);

        BizException ex = assertThrows(BizException.class, () ->
                savedPlaceService.createSavedPlace("u-1",
                        new CreateSavedPlaceRequest(null, "POI", "ATTRACTION", location, null)));
        assertEquals(ErrorCode.SAVED_PLACE_ALREADY_EXISTS, ex.getErrorCode());
        verify(savedPlaceMapper, never()).insert(any(SavedPlace.class));
    }

    @Test
    void createSavedPlace_mapsDuplicateKeyToAlreadyExists() {
        LocationCandidate location = location("浅草寺", null);
        Place place = activePoi("place-1", null);
        when(placeService.resolveOrCreatePlace(location, "POI", "ATTRACTION")).thenReturn(place);
        when(savedPlaceMapper.selectOne(any(QueryWrapper.class))).thenReturn(null);
        when(savedPlaceMapper.insert(any(SavedPlace.class))).thenThrow(new DuplicateKeyException("dup"));

        BizException ex = assertThrows(BizException.class, () ->
                savedPlaceService.createSavedPlace("u-1",
                        new CreateSavedPlaceRequest(null, "POI", "ATTRACTION", location, null)));
        assertEquals(ErrorCode.SAVED_PLACE_ALREADY_EXISTS, ex.getErrorCode());
    }

    @Test
    void createSavedPlace_existingPlaceIdCreatesRelationWithoutResolvingLocation() {
        Place place = activePoi("place-1", "adcode:131016");
        when(placeService.getPlaceById("place-1")).thenReturn(place);
        when(savedPlaceMapper.selectOne(any(QueryWrapper.class))).thenReturn(null);
        doAnswer(inv -> {
            SavedPlace sp = inv.getArgument(0);
            sp.setId("sp-1");
            sp.setCreatedAt(LocalDateTime.of(2026, 7, 1, 0, 0));
            sp.setUpdatedAt(LocalDateTime.of(2026, 7, 1, 0, 0));
            return 1;
        }).when(savedPlaceMapper).insert(any(SavedPlace.class));
        when(savedPlaceUsageMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of());

        var response = savedPlaceService.createSavedPlace("u-1",
                new CreateSavedPlaceRequest("place-1", null, null, null, "详情页收藏"));

        assertEquals("place-1", response.placeId());
        verify(placeService, never()).resolveOrCreatePlace(any(), anyString(), anyString());
        ArgumentCaptor<SavedPlace> captor = ArgumentCaptor.forClass(SavedPlace.class);
        verify(savedPlaceMapper).insert(captor.capture());
        assertEquals("MANUAL", captor.getValue().getSource());
    }

    @Test
    void createSavedPlace_rejectsBothPlaceIdAndLocation() {
        BizException ex = assertThrows(BizException.class, () ->
                savedPlaceService.createSavedPlace("u-1",
                        new CreateSavedPlaceRequest(
                                "place-1", "POI", "ATTRACTION", location("浅草寺", null), null)));

        assertEquals(ErrorCode.PLACE_INVALID_SCOPE_CATEGORY, ex.getErrorCode());
        verify(placeService, never()).getPlaceById(anyString());
        verify(placeService, never()).resolveOrCreatePlace(any(), anyString(), anyString());
    }

    @Test
    void createSavedPlace_rejectsMissingPlaceIdAndLocation() {
        BizException ex = assertThrows(BizException.class, () ->
                savedPlaceService.createSavedPlace("u-1",
                        new CreateSavedPlaceRequest(null, null, null, null, null)));

        assertEquals(ErrorCode.PLACE_INVALID_SCOPE_CATEGORY, ex.getErrorCode());
    }

    @Test
    void createSavedPlace_rejectsClassificationThatDiffersFromExistingPlace() {
        Place place = activePoi("place-1", null);
        when(placeService.getPlaceById("place-1")).thenReturn(place);

        BizException ex = assertThrows(BizException.class, () ->
                savedPlaceService.createSavedPlace("u-1",
                        new CreateSavedPlaceRequest("place-1", "POI", "RESTAURANT", null, null)));

        assertEquals(ErrorCode.PLACE_INVALID_SCOPE_CATEGORY, ex.getErrorCode());
    }

    @Test
    void resolveForTripDestination_rejectsPoi() {
        SavedPlace sp = ownedSaved("sp-1", "u-1", "place-1");
        when(savedPlaceMapper.selectById("sp-1")).thenReturn(sp);
        Place poi = activePoi("place-1", null);
        when(placeService.getPlaceById("place-1")).thenReturn(poi);

        BizException ex = assertThrows(BizException.class,
                () -> savedPlaceService.resolveForTripDestination("u-1", "sp-1"));
        assertEquals(ErrorCode.SAVED_PLACE_SCOPE_MISMATCH, ex.getErrorCode());
    }

    @Test
    void resolveForTripDestination_hidesOtherUsers() {
        SavedPlace sp = ownedSaved("sp-1", "u-other", "place-1");
        when(savedPlaceMapper.selectById("sp-1")).thenReturn(sp);

        NotFoundException ex = assertThrows(NotFoundException.class,
                () -> savedPlaceService.resolveForTripDestination("u-1", "sp-1"));
        assertEquals(ErrorCode.SAVED_PLACE_NOT_FOUND, ex.getErrorCode());
    }

    @Test
    void resolveForItineraryNode_rejectsDestinationMismatch() {
        SavedPlace sp = ownedSaved("sp-1", "u-1", "place-1");
        when(savedPlaceMapper.selectById("sp-1")).thenReturn(sp);
        Place poi = activePoi("place-1", "adcode:110000");
        when(placeService.getPlaceById("place-1")).thenReturn(poi);

        Trip trip = new Trip();
        trip.setId("trip-1");
        trip.setUserId("u-1");
        trip.setDestinationAdcode("310000");
        stubTripAccess(trip);

        BizException ex = assertThrows(BizException.class,
                () -> savedPlaceService.resolveForItineraryNode("u-1", "sp-1", "trip-1"));
        assertEquals(ErrorCode.SAVED_PLACE_DESTINATION_MISMATCH, ex.getErrorCode());
    }

    @Test
    void resolveForItineraryNode_matchesDestinationKey() {
        SavedPlace sp = ownedSaved("sp-1", "u-1", "place-1");
        when(savedPlaceMapper.selectById("sp-1")).thenReturn(sp);
        Place poi = activePoi("place-1", "adcode:131016");
        when(placeService.getPlaceById("place-1")).thenReturn(poi);

        Trip trip = new Trip();
        trip.setId("trip-1");
        trip.setUserId("u-1");
        trip.setDestinationAdcode("131016");
        stubTripAccess(trip);

        Place resolved = savedPlaceService.resolveForItineraryNode("u-1", "sp-1", "trip-1");
        assertEquals("place-1", resolved.getId());
    }

    @Test
    void resolveForItineraryNode_rejectsMissingDestinationMatchData() {
        SavedPlace sp = ownedSaved("sp-1", "u-1", "place-1");
        when(savedPlaceMapper.selectById("sp-1")).thenReturn(sp);
        Place poi = activePoi("place-1", null);
        when(placeService.getPlaceById("place-1")).thenReturn(poi);

        Trip trip = new Trip();
        trip.setId("trip-1");
        trip.setUserId("u-1");
        stubTripAccess(trip);

        BizException ex = assertThrows(BizException.class,
                () -> savedPlaceService.resolveForItineraryNode("u-1", "sp-1", "trip-1"));

        assertEquals(ErrorCode.SAVED_PLACE_DESTINATION_MISMATCH, ex.getErrorCode());
    }

    @Test
    void recordNodeUsage_writesUsage() {
        SavedPlace sp = ownedSaved("sp-1", "u-1", "place-1");
        when(savedPlaceMapper.selectById("sp-1")).thenReturn(sp);

        savedPlaceService.recordNodeUsage("u-1", "sp-1", "trip-1", "node-1");

        ArgumentCaptor<SavedPlaceUsage> captor = ArgumentCaptor.forClass(SavedPlaceUsage.class);
        verify(savedPlaceUsageMapper).insertNodeUsageIfAbsent(captor.capture());
        assertEquals("ITINERARY_NODE", captor.getValue().getUsageType());
        assertEquals("node-1", captor.getValue().getItineraryNodeId());
    }

    @Test
    void buildUserContext_notSaved() {
        when(savedPlaceMapper.selectOne(any(QueryWrapper.class))).thenReturn(null);
        var ctx = savedPlaceService.buildUserContext("u-1", "place-1");
        assertFalse(ctx.isSaved());
    }

    @Test
    void buildUserContext_savedWithUsage() {
        SavedPlace sp = ownedSaved("sp-1", "u-1", "place-1");
        when(savedPlaceMapper.selectOne(any(QueryWrapper.class))).thenReturn(sp);
        SavedPlaceUsage usage = new SavedPlaceUsage();
        usage.setTripId("trip-9");
        when(savedPlaceUsageMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(usage));

        var ctx = savedPlaceService.buildUserContext("u-1", "place-1");
        assertTrue(ctx.isSaved());
        assertEquals("sp-1", ctx.savedPlaceId());
        assertEquals(1L, ctx.usageSummary().tripCount());
        assertEquals("trip-9", ctx.usageSummary().lastUsedTripId());
    }

    @Test
    void findSavedPlaceIds_returnsEmptyWithoutQueryWhenNoPlaceIds() {
        assertTrue(savedPlaceService.findSavedPlaceIds("u-1", List.of()).isEmpty());
        verify(savedPlaceMapper, never()).selectList(any());
    }

    @Test
    void findSavedPlaceIds_returnsSavedSubset() {
        when(savedPlaceMapper.selectList(any(QueryWrapper.class)))
                .thenReturn(List.of(ownedSaved("sp-1", "u-1", "place-1")));

        Set<String> ids = savedPlaceService.findSavedPlaceIds("u-1", List.of("place-1", "place-2"));

        assertEquals(Set.of("place-1"), ids);
    }

    @Test
    void updateSavedPlace_updatesNote() {
        SavedPlace sp = ownedSaved("sp-1", "u-1", "place-1");
        when(savedPlaceMapper.selectById("sp-1")).thenReturn(sp);
        Place place = activePoi("place-1", null);
        when(placeService.getPlaceById("place-1")).thenReturn(place);
        when(savedPlaceUsageMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of());

        var resp = savedPlaceService.updateSavedPlace("u-1", "sp-1", new UpdateSavedPlaceRequest("新备注"));
        assertEquals("新备注", resp.note());
        verify(savedPlaceMapper).updateById(sp);
    }

    @Test
    void listSavedPlaces_clampsPageSize() {
        when(savedPlaceMapper.selectPage(any(Page.class), any(QueryWrapper.class))).thenAnswer(inv -> {
            Page<SavedPlace> page = inv.getArgument(0);
            assertEquals(100, page.getSize());
            page.setRecords(List.of());
            page.setTotal(0);
            return page;
        });

        savedPlaceService.listSavedPlaces("u-1", null, null, null, null, null, 1, 999);
    }

    @Test
    void listSavedPlaces_keywordAppliesNoteAndPlaceFilter() {
        when(savedPlaceMapper.selectPage(any(Page.class), any(QueryWrapper.class))).thenAnswer(inv -> {
            QueryWrapper<?> qw = inv.getArgument(1);
            assertTrue(qw.getCustomSqlSegment().contains("note LIKE"));
            assertEquals(1, inv.getArgument(0, Page.class).getCurrent());
            assertEquals(20, inv.getArgument(0, Page.class).getSize());
            Page<SavedPlace> page = inv.getArgument(0);
            page.setRecords(List.of());
            page.setTotal(0);
            return page;
        });

        savedPlaceService.listSavedPlaces("u-1", "想去京都", null, null, null, null, 1, 20);
        verify(savedPlaceMapper).selectPage(any(Page.class), any(QueryWrapper.class));
    }

    @Test
    void listSavedPlaces_itineraryUsageRequiresTripId() {
        BizException ex = assertThrows(BizException.class, () ->
                savedPlaceService.listSavedPlaces(
                        "u-1", null, null, null, "ITINERARY_NODE", null, 1, 20));

        assertEquals(ErrorCode.PARAM_INVALID, ex.getErrorCode());
        verify(savedPlaceMapper, never()).selectPage(any(Page.class), any(QueryWrapper.class));
    }

    @Test
    void listSavedPlaces_itineraryUsageExcludesPlacesWithoutDestinationKey() {
        Trip trip = new Trip();
        trip.setId("trip-1");
        trip.setUserId("u-1");
        trip.setDestinationAdcode("131016");
        stubTripAccess(trip);
        when(savedPlaceMapper.selectPage(any(Page.class), any(QueryWrapper.class))).thenAnswer(inv -> {
            QueryWrapper<?> qw = inv.getArgument(1);
            String sql = qw.getCustomSqlSegment();
            assertTrue(sql.contains("p.destination_key ="));
            assertFalse(sql.contains("p.destination_key IS NULL"));
            Page<SavedPlace> result = inv.getArgument(0);
            result.setRecords(List.of());
            result.setTotal(0);
            return result;
        });

        savedPlaceService.listSavedPlaces(
                "u-1", null, null, null, "ITINERARY_NODE", "trip-1", 1, 20);

        verify(savedPlaceMapper).selectPage(any(Page.class), any(QueryWrapper.class));
    }

    @Test
    void listSavedPlaces_itineraryUsageReturnsEmptyWhenTripHasNoDestinationIdentity() {
        Trip trip = new Trip();
        trip.setId("trip-1");
        trip.setUserId("u-1");
        stubTripAccess(trip);
        when(savedPlaceMapper.selectPage(any(Page.class), any(QueryWrapper.class))).thenAnswer(inv -> {
            QueryWrapper<?> qw = inv.getArgument(1);
            assertTrue(qw.getCustomSqlSegment().contains("1 = 0"));
            Page<SavedPlace> result = inv.getArgument(0);
            result.setRecords(List.of());
            result.setTotal(0);
            return result;
        });

        savedPlaceService.listSavedPlaces(
                "u-1", null, null, null, "ITINERARY_NODE", "trip-1", 1, 20);

        verify(savedPlaceMapper).selectPage(any(Page.class), any(QueryWrapper.class));
    }

    private void stubTripAccess(Trip trip) {
        TripMember member = new TripMember();
        member.setId("m-1");
        member.setUserId(trip.getUserId());
        member.setRole("owner");
        when(tripAccessService.requirePermission(eq(trip.getId()), eq(trip.getUserId()), eq(TripPermission.READ)))
                .thenReturn(new TripAccessService.TripAccess(
                        trip, member, TripMemberRole.OWNER, TripMemberRole.OWNER.permissions()));
    }

    private static SavedPlace ownedSaved(String id, String userId, String placeId) {
        SavedPlace sp = new SavedPlace();
        sp.setId(id);
        sp.setUserId(userId);
        sp.setPlaceId(placeId);
        sp.setCreatedAt(LocalDateTime.of(2026, 7, 1, 0, 0));
        sp.setUpdatedAt(LocalDateTime.of(2026, 7, 1, 0, 0));
        return sp;
    }

    private static Place activePoi(String id, String destinationKey) {
        Place place = new Place();
        place.setId(id);
        place.setPlaceScope(PlaceScope.POI.name());
        place.setCategory("ATTRACTION");
        place.setCanonicalName("浅草寺");
        place.setDestinationKey(destinationKey);
        place.setStatus("ACTIVE");
        return place;
    }

    private static LocationCandidate location(String name, String adcode) {
        return new LocationCandidate(
                name, null, "AMAP", "poi-1", "日本", "东京",
                new BigDecimal("35.7"), new BigDecimal("139.7"), adcode, null);
    }
}
