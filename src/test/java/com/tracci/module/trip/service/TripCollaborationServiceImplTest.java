package com.tracci.module.trip.service;

import com.tracci.common.context.LoginUserContext;
import com.tracci.common.exception.BizException;
import com.tracci.common.exception.NotFoundException;
import com.tracci.common.response.ErrorCode;
import com.tracci.integration.cos.FileUrlResolver;
import com.tracci.module.expense.repository.ExpenseMapper;
import com.tracci.module.expense.repository.ExpenseSplitMapper;
import com.tracci.module.file.entity.File;
import com.tracci.module.file.repository.FileMapper;
import com.tracci.module.trip.dto.request.TripInvitationCreateReq;
import com.tracci.module.trip.dto.request.TripInvitationJoinReq;
import com.tracci.module.trip.dto.request.TripMemberUpdateReq;
import com.tracci.module.trip.dto.request.TripOwnershipTransferReq;
import com.tracci.module.trip.entity.Trip;
import com.tracci.module.trip.entity.TripInvitation;
import com.tracci.module.trip.entity.TripMember;
import com.tracci.module.trip.enums.TripPermission;
import com.tracci.module.trip.repository.TripInvitationMapper;
import com.tracci.module.trip.repository.TripMapper;
import com.tracci.module.trip.repository.TripMemberMapper;
import com.tracci.module.user.entity.User;
import com.tracci.module.user.repository.UserMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TripCollaborationServiceImplTest {

    @Mock
    private TripAccessService tripAccessService;
    @Mock
    private TripInvitationMapper invitationMapper;
    @Mock
    private TripMapper tripMapper;
    @Mock
    private TripMemberMapper tripMemberMapper;
    @Mock
    private UserMapper userMapper;
    @Mock
    private FileMapper fileMapper;
    @Mock
    private FileUrlResolver fileUrlResolver;
    @Mock
    private ExpenseMapper expenseMapper;
    @Mock
    private ExpenseSplitMapper expenseSplitMapper;
    @Mock
    private TripActivityLogService activityLogService;

    @InjectMocks
    private TripCollaborationServiceImpl service;

    @AfterEach
    void tearDown() {
        LoginUserContext.clear();
    }

    @Test
    void createInvitation_returnsCurrentCoEditorPaths() {
        LoginUserContext.setUserId("owner-1");
        Trip trip = new Trip();
        trip.setId("trip-1");
        trip.setTitle("东京 5 日");
        when(tripAccessService.requirePermission("trip-1", "owner-1", TripPermission.CREATE_INVITATION))
                .thenReturn(new TripAccessService.TripAccess(trip, member("owner-member", "owner")));
        doAnswer(invocation -> {
            TripInvitation invitation = invocation.getArgument(0);
            invitation.setId("invite-1");
            return 1;
        }).when(invitationMapper).insert(any(TripInvitation.class));

        var response = service.createInvitation("trip-1", new TripInvitationCreateReq("editor", 168, 10));

        assertEquals(4, response.inviteCode().length());
        assertEquals("/pages/co-editor/index?id=trip-1&code=" + response.inviteCode(), response.miniProgramPath());
        assertEquals("tracci://co-editor?id=trip-1&code=" + response.inviteCode(), response.appDeepLink());
    }

    @Test
    void joinInvitation_rejectsTripWithoutExactlyOneEffectiveOwner() {
        LoginUserContext.setUserId("user-2");
        TripInvitation invitation = new TripInvitation();
        invitation.setId("invite-1");
        invitation.setTripId("trip-1");
        invitation.setRoleToGrant("editor");
        invitation.setUsedCount(0);
        invitation.setMaxUses(10);
        invitation.setExpiresAt(LocalDateTime.now().plusHours(1));
        when(invitationMapper.findByCodeHash(
                TripCollaborationServiceImpl.hashCode("8K3M"))).thenReturn(invitation);
        Trip trip = new Trip();
        trip.setId("trip-1");
        when(tripMemberMapper.countEffectiveActiveOwnersByTripId("trip-1")).thenReturn(0L);

        BizException ex = assertThrows(BizException.class, () -> service.joinInvitation(
                new TripInvitationJoinReq("8K3M")));
        assertEquals(ErrorCode.TRIP_OWNER_INVARIANT_VIOLATION, ex.getErrorCode());

        verify(tripMapper, never()).lockByIdForUpdate(any(String.class));
        verify(tripMemberMapper, never()).insert(any(TripMember.class));
        verify(invitationMapper, never()).incrementUsedCount(any(String.class));
    }

    @Test
    void removeMember_rejectsMemberReferencedByAaExpense() {
        LoginUserContext.setUserId("owner-1");
        when(tripAccessService.requirePermission("trip-1", "owner-1", TripPermission.MANAGE_MEMBERS))
                .thenReturn(new TripAccessService.TripAccess(new Trip(), member("owner-member", "owner")));
        when(tripMemberMapper.findEffectiveActiveByIdAndTripId("member-2", "trip-1"))
                .thenReturn(member("member-2", "editor"));
        when(expenseMapper.countGroupExpensesByPayerMemberId("trip-1", "member-2"))
                .thenReturn(1L);

        assertThrows(BizException.class, () -> service.removeMember("trip-1", "member-2"));
        verify(tripMemberMapper, never()).removeMember("trip-1", "member-2");
    }

    @Test
    void transferOwnership_updatesCanonicalOwnerAndReturnsDowngradedAccess() {
        LoginUserContext.setUserId("owner-1");
        Trip trip = new Trip();
        trip.setId("trip-1");
        TripMember owner = member("owner-member", "owner");
        owner.setUserId("owner-1");
        TripMember target = member("member-2", "editor");
        target.setUserId("user-2");

        when(tripMapper.lockByIdForUpdate("trip-1")).thenReturn(trip);
        when(tripAccessService.requirePermission(
                "trip-1", "owner-1", TripPermission.TRANSFER_OWNERSHIP))
                .thenReturn(new TripAccessService.TripAccess(trip, owner));
        when(tripMemberMapper.findEffectiveActiveByIdAndTripId("member-2", "trip-1"))
                .thenReturn(target);
        when(tripMemberMapper.demoteOwner("trip-1", "owner-member")).thenReturn(1);
        when(tripMemberMapper.promoteOwner("trip-1", "member-2")).thenReturn(1);
        when(tripMapper.updateOwnerUserId("trip-1", "user-2")).thenReturn(1);

        var response = service.transferOwnership(
                "trip-1", new TripOwnershipTransferReq("member-2"));

        assertEquals("owner-member", response.previousOwnerMemberId());
        assertEquals("member-2", response.newOwnerMemberId());
        assertEquals("user-2", response.newOwnerUserId());
        assertEquals("editor", response.access().role());
        assertEquals(false, response.access().permissions().contains(
                TripPermission.TRANSFER_OWNERSHIP.value()));
        verify(activityLogService).record(
                eq("trip-1"), eq("owner-1"), eq("owner-member"),
                eq("TRIP_OWNERSHIP_TRANSFERRED"), eq("MEMBER"), eq("member-2"),
                eq(List.of()), any(String.class));
    }

    @Test
    void transferOwnership_rejectsDeletedOrInactiveTarget() {
        LoginUserContext.setUserId("owner-1");
        Trip trip = new Trip();
        trip.setId("trip-1");
        TripMember owner = member("owner-member", "owner");
        owner.setUserId("owner-1");
        when(tripMapper.lockByIdForUpdate("trip-1")).thenReturn(trip);
        when(tripAccessService.requirePermission(
                "trip-1", "owner-1", TripPermission.TRANSFER_OWNERSHIP))
                .thenReturn(new TripAccessService.TripAccess(trip, owner));
        when(tripMemberMapper.findEffectiveActiveByIdAndTripId("member-2", "trip-1"))
                .thenReturn(null);

        assertThrows(BizException.class, () -> service.transferOwnership(
                "trip-1", new TripOwnershipTransferReq("member-2")));

        verify(tripMemberMapper, never()).demoteOwner(any(), any());
        verify(tripMemberMapper, never()).promoteOwner(any(), any());
        verify(tripMapper, never()).updateOwnerUserId(any(), any());
    }

    @Test
    void transferOwnership_rollsBackOnAtomicUpdateFailure() {
        LoginUserContext.setUserId("owner-1");
        Trip trip = new Trip();
        trip.setId("trip-1");
        TripMember owner = member("owner-member", "owner");
        owner.setUserId("owner-1");
        TripMember target = member("member-2", "editor");
        target.setUserId("user-2");

        when(tripMapper.lockByIdForUpdate("trip-1")).thenReturn(trip);
        when(tripAccessService.requirePermission(
                "trip-1", "owner-1", TripPermission.TRANSFER_OWNERSHIP))
                .thenReturn(new TripAccessService.TripAccess(trip, owner));
        when(tripMemberMapper.findEffectiveActiveByIdAndTripId("member-2", "trip-1"))
                .thenReturn(target);
        when(tripMemberMapper.demoteOwner("trip-1", "owner-member")).thenReturn(1);
        when(tripMemberMapper.promoteOwner("trip-1", "member-2")).thenReturn(0);

        assertThrows(BizException.class, () -> service.transferOwnership(
                "trip-1", new TripOwnershipTransferReq("member-2")));

        verify(tripMapper, never()).updateOwnerUserId(any(), any());
    }

    @Test
    void updateMember_detectsConcurrentRoleChange() {
        LoginUserContext.setUserId("owner-1");
        Trip trip = new Trip();
        trip.setId("trip-1");
        TripMember owner = member("owner-member", "owner");
        owner.setUserId("owner-1");

        when(tripAccessService.requirePermission("trip-1", "owner-1", TripPermission.MANAGE_MEMBERS))
                .thenReturn(new TripAccessService.TripAccess(trip, owner));
        // 第一次 requireEffectiveMember 返回 active 成员
        TripMember editor = member("member-editor", "editor");
        when(tripMemberMapper.findEffectiveActiveByIdAndTripId("member-editor", "trip-1"))
                .thenReturn(editor);
        when(tripMemberMapper.updateRole("trip-1", "member-editor", "viewer")).thenReturn(1);
        // updateRole 后成员已失效（模拟被其他事务移除），第二次 requireEffectiveMember 返回 null
        when(tripMemberMapper.findEffectiveActiveByIdAndTripId("member-editor", "trip-1"))
                .thenReturn(editor).thenReturn(null);

        assertThrows(NotFoundException.class, () -> service.updateMember(
                "trip-1", "member-editor", new TripMemberUpdateReq("viewer")));
    }

    @Test
    void removeMember_detectsConcurrentRemovalFailure() {
        LoginUserContext.setUserId("owner-1");
        Trip trip = new Trip();
        trip.setId("trip-1");
        TripMember owner = member("owner-member", "owner");
        owner.setUserId("owner-1");

        when(tripAccessService.requirePermission("trip-1", "owner-1", TripPermission.MANAGE_MEMBERS))
                .thenReturn(new TripAccessService.TripAccess(trip, owner));
        TripMember editor = member("member-editor", "editor");
        when(tripMemberMapper.findEffectiveActiveByIdAndTripId("member-editor", "trip-1"))
                .thenReturn(editor);
        when(expenseMapper.countGroupExpensesByPayerMemberId("trip-1", "member-editor")).thenReturn(0L);
        when(expenseSplitMapper.countActiveByTripIdAndMemberId("trip-1", "member-editor")).thenReturn(0L);
        when(tripMemberMapper.removeMember("trip-1", "member-editor")).thenReturn(1);
        // removeMember 后成员仍存在（模拟回滚/并发恢复）
        when(tripMemberMapper.findEffectiveActiveByIdAndTripId("member-editor", "trip-1"))
                .thenReturn(editor);

        assertThrows(BizException.class, () -> service.removeMember("trip-1", "member-editor"));
    }

    @Test
    void listMembers_returnsResolvedAvatarUrl() {
        LoginUserContext.setUserId("owner-1");
        TripMember member = member("member-1", "owner");
        member.setUserId("owner-1");
        User user = new User();
        user.setId("owner-1");
        user.setNickname("Liz");
        user.setAvatarFileId("file-1");
        File avatar = new File();
        avatar.setId("file-1");
        avatar.setUserId("owner-1");
        avatar.setStatus("UPLOADED");
        avatar.setObjectKey("users/owner-1/avatar.jpg");

        when(tripMemberMapper.listActiveByTripId("trip-1")).thenReturn(java.util.List.of(member));
        when(userMapper.selectBatchIds(anyCollection())).thenReturn(List.of(user));
        when(fileMapper.selectById("file-1")).thenReturn(avatar);
        when(fileUrlResolver.resolve(avatar)).thenReturn("https://example.com/avatar.jpg?sign=temporary");

        var members = service.listMembers("trip-1");

        assertEquals(1, members.size());
        assertEquals("Liz", members.getFirst().nickname());
        assertEquals("https://example.com/avatar.jpg?sign=temporary", members.getFirst().avatarUrl());
    }

    @Test
    void listMembers_omitsAvatarUrlWhenFileBelongsToAnotherUser() {
        LoginUserContext.setUserId("owner-1");
        TripMember member = member("member-1", "owner");
        member.setUserId("owner-1");
        User user = new User();
        user.setId("owner-1");
        user.setNickname("Liz");
        user.setAvatarFileId("file-1");
        File avatar = new File();
        avatar.setId("file-1");
        avatar.setUserId("other-user");
        avatar.setStatus("UPLOADED");

        when(tripMemberMapper.listActiveByTripId("trip-1")).thenReturn(java.util.List.of(member));
        when(userMapper.selectBatchIds(anyCollection())).thenReturn(List.of(user));
        when(fileMapper.selectById("file-1")).thenReturn(avatar);

        var members = service.listMembers("trip-1");

        assertNull(members.getFirst().avatarUrl());
        verify(fileUrlResolver, never()).resolve(any(File.class));
    }

    @Test
    void listMembers_keepsOwnerRoleWhenAnotherMemberUserWasDeleted() {
        LoginUserContext.setUserId("owner-1");
        TripMember owner = member("member-owner", "owner");
        owner.setUserId("owner-1");
        TripMember deletedMember = member("member-deleted", "editor");
        deletedMember.setUserId("deleted-user");
        User ownerUser = new User();
        ownerUser.setId("owner-1");
        ownerUser.setNickname("Liz");

        when(tripMemberMapper.listActiveByTripId("trip-1")).thenReturn(List.of(owner, deletedMember));
        // selectBatchIds 会过滤逻辑删除用户，因此这里只返回仍有效的 owner。
        when(userMapper.selectBatchIds(anyCollection())).thenReturn(List.of(ownerUser));

        var members = service.listMembers("trip-1");

        assertEquals(2, members.size());
        assertEquals("owner", members.getFirst().role());
        assertEquals("Liz", members.getFirst().nickname());
        assertEquals("active", members.getFirst().accountStatus());
        assertEquals("已注销用户", members.get(1).nickname());
        assertEquals("deleted", members.get(1).accountStatus());
    }

    private TripMember member(String id, String role) {
        TripMember member = new TripMember();
        member.setId(id);
        member.setTripId("trip-1");
        member.setRole(role);
        member.setStatus("active");
        return member;
    }
}
