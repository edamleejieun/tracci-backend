package com.tracci.module.trip.service;

import com.tracci.common.exception.ForbiddenException;
import com.tracci.common.exception.NotFoundException;
import com.tracci.module.trip.entity.Trip;
import com.tracci.module.trip.entity.TripMember;
import com.tracci.module.trip.enums.TripPermission;
import com.tracci.module.trip.repository.TripMapper;
import com.tracci.module.trip.repository.TripMemberMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TripAccessServiceImplTest {

    @Mock
    private TripMapper tripMapper;

    @Mock
    private TripMemberMapper tripMemberMapper;

    @InjectMocks
    private TripAccessServiceImpl service;

    @Test
    void requirePermission_readAllowsAllActiveRoles() {
        Trip trip = trip();
        when(tripMapper.selectById("trip-1")).thenReturn(trip);
        when(tripMemberMapper.findActiveByTripIdAndUserId("trip-1", "u-1")).thenReturn(member("viewer"));

        TripAccessService.TripAccess access = service.requirePermission(
                "trip-1", "u-1", TripPermission.READ);

        assertEquals(trip, access.trip());
        assertEquals("viewer", access.member().getRole());
    }

    @Test
    void requirePermission_editContentRejectsViewer() {
        when(tripMapper.selectById("trip-1")).thenReturn(trip());
        when(tripMemberMapper.findActiveByTripIdAndUserId("trip-1", "u-1")).thenReturn(member("viewer"));

        assertThrows(ForbiddenException.class, () -> service.requirePermission(
                "trip-1", "u-1", TripPermission.EDIT_CONTENT));
    }

    @Test
    void requirePermission_editContentAllowsEditor() {
        when(tripMapper.selectById("trip-1")).thenReturn(trip());
        when(tripMemberMapper.findActiveByTripIdAndUserId("trip-1", "u-1")).thenReturn(member("editor"));

        TripAccessService.TripAccess access = service.requirePermission(
                "trip-1", "u-1", TripPermission.EDIT_CONTENT);

        assertEquals("editor", access.member().getRole());
    }

    @Test
    void requirePermission_manageMembersRejectsEditor() {
        when(tripMapper.selectById("trip-1")).thenReturn(trip());
        when(tripMemberMapper.findActiveByTripIdAndUserId("trip-1", "u-1")).thenReturn(member("editor"));

        assertThrows(ForbiddenException.class, () -> service.requirePermission(
                "trip-1", "u-1", TripPermission.MANAGE_MEMBERS));
    }

    @Test
    void requirePermission_manageMembersAllowsOwner() {
        when(tripMapper.selectById("trip-1")).thenReturn(trip());
        when(tripMemberMapper.findActiveByTripIdAndUserId("trip-1", "u-1")).thenReturn(member("owner"));

        TripAccessService.TripAccess access = service.requirePermission(
                "trip-1", "u-1", TripPermission.MANAGE_MEMBERS);

        assertEquals("owner", access.member().getRole());
    }

    @Test
    void requirePermission_editorCapabilityMatrixIsCentralized() {
        when(tripMapper.selectById("trip-1")).thenReturn(trip());
        when(tripMemberMapper.findActiveByTripIdAndUserId("trip-1", "u-1")).thenReturn(member("editor"));

        TripAccessService.TripAccess access = service.requirePermission(
                "trip-1", "u-1", TripPermission.CREATE_INVITATION);

        assertEquals("editor", access.role().value());
        assertEquals(true, access.allows(TripPermission.READ));
        assertEquals(true, access.allows(TripPermission.EDIT_CONTENT));
        assertEquals(false, access.allows(TripPermission.UPDATE_TRIP));
        assertThrows(ForbiddenException.class, () -> service.requirePermission(
                "trip-1", "u-1", TripPermission.MANAGE_MEMBERS));
    }

    @Test
    void requirePermission_nonMemberRejected() {
        when(tripMapper.selectById("trip-1")).thenReturn(trip());
        when(tripMemberMapper.findActiveByTripIdAndUserId("trip-1", "u-1")).thenReturn(null);

        assertThrows(ForbiddenException.class, () -> service.requirePermission(
                "trip-1", "u-1", TripPermission.READ));
    }

    @Test
    void requirePermission_missingTripRejected() {
        when(tripMapper.selectById("trip-1")).thenReturn(null);

        assertThrows(NotFoundException.class, () -> service.requirePermission(
                "trip-1", "u-1", TripPermission.READ));
    }

    private Trip trip() {
        Trip trip = new Trip();
        trip.setId("trip-1");
        trip.setUserId("owner-1");
        return trip;
    }

    private TripMember member(String role) {
        TripMember member = new TripMember();
        member.setId("member-1");
        member.setTripId("trip-1");
        member.setUserId("u-1");
        member.setRole(role);
        member.setStatus("active");
        return member;
    }
}
