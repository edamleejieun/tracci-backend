package com.tracci.module.trip.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.tracci.common.context.LoginUserContext;
import com.tracci.common.exception.BizException;
import com.tracci.common.exception.NotFoundException;
import com.tracci.common.response.ErrorCode;
import com.tracci.common.util.DateTimeUtil;
import com.tracci.integration.cos.FileUrlResolver;
import com.tracci.module.file.entity.File;
import com.tracci.module.file.repository.FileMapper;
import com.tracci.module.trip.dto.request.TripInvitationCreateReq;
import com.tracci.module.trip.dto.request.TripInvitationJoinReq;
import com.tracci.module.trip.dto.request.TripMemberUpdateReq;
import com.tracci.module.trip.dto.response.TripInvitationCreateResp;
import com.tracci.module.trip.dto.response.TripInvitationJoinResp;
import com.tracci.module.trip.dto.response.TripAccessResp;
import com.tracci.module.trip.dto.response.TripMemberResp;
import com.tracci.module.trip.dto.response.TripOwnershipTransferResp;
import com.tracci.module.trip.dto.request.TripOwnershipTransferReq;
import com.tracci.module.trip.entity.Trip;
import com.tracci.module.trip.entity.TripInvitation;
import com.tracci.module.trip.entity.TripMember;
import com.tracci.module.trip.enums.TripMemberRole;
import com.tracci.module.trip.enums.TripPermission;
import com.tracci.module.trip.repository.TripInvitationMapper;
import com.tracci.module.trip.repository.TripMapper;
import com.tracci.module.trip.repository.TripMemberMapper;
import com.tracci.module.expense.repository.ExpenseMapper;
import com.tracci.module.expense.repository.ExpenseSplitMapper;
import com.tracci.module.user.entity.User;
import com.tracci.module.user.repository.UserMapper;
import com.tracci.module.user.support.UserDisplaySupport;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TripCollaborationServiceImpl implements TripCollaborationService {

    private static final String ACTIVE = "active";
    private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int CODE_LENGTH = 4;
    private static final String WEB_INVITE_BASE_URL = "https://tracci.cn/co-editor";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final TripAccessService tripAccessService;
    private final TripInvitationMapper invitationMapper;
    private final TripMapper tripMapper;
    private final TripMemberMapper tripMemberMapper;
    private final UserMapper userMapper;
    private final FileMapper fileMapper;
    private final FileUrlResolver fileUrlResolver;
    private final ExpenseMapper expenseMapper;
    private final ExpenseSplitMapper expenseSplitMapper;
    private final TripActivityLogService activityLogService;

    @Override
    @Transactional
    public TripInvitationCreateResp createInvitation(String tripId, TripInvitationCreateReq req) {
        String userId = LoginUserContext.requireUserId();
        TripAccessService.TripAccess access =
                tripAccessService.requirePermission(tripId, userId, TripPermission.CREATE_INVITATION);
        Trip trip = access.trip();
        String role = normalizeGrantRole(req.roleToGrant());
        int expiresInHours = req.expiresInHours() == null ? 168 : req.expiresInHours();
        int maxUses = req.maxUses() == null ? 10 : req.maxUses();

        String code = null;
        TripInvitation invitation = null;
        for (int attempt = 0; attempt < 5; attempt++) {
            code = generateCode();
            invitation = new TripInvitation();
            invitation.setTripId(tripId);
            invitation.setCreatedByUserId(userId);
            invitation.setInviteCodeHash(hashCode(code));
            invitation.setRoleToGrant(role);
            invitation.setExpiresAt(DateTimeUtil.nowUtc().plusHours(expiresInHours));
            invitation.setMaxUses(maxUses);
            invitation.setUsedCount(0);
            try {
                invitationMapper.insert(invitation);
                break;
            } catch (DuplicateKeyException ignored) {
                invitation = null;
            }
        }
        if (invitation == null) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "邀请码生成失败，请重试");
        }

        String path = "/pages/co-editor/index?id=" + tripId + "&code=" + code;
        String deepLink = "tracci://co-editor?id=" + tripId + "&code=" + code;
        String webInviteUrl = WEB_INVITE_BASE_URL + "?id=" + tripId + "&code=" + code;
        String shareText = "邀请你一起编辑趣溪旅行：" + trip.getTitle()
                + "。点击链接加入：" + webInviteUrl + "。邀请码：" + code;
        return new TripInvitationCreateResp(invitation.getId(), tripId, code, role,
                DateTimeUtil.toInstantString(invitation.getExpiresAt()), maxUses, 0,
                webInviteUrl, path, deepLink, shareText);
    }

    @Override
    @Transactional
    public TripInvitationJoinResp joinInvitation(TripInvitationJoinReq req) {
        String userId = LoginUserContext.requireUserId();
        String code = normalizeCode(req.inviteCode());
        TripInvitation invitation = invitationMapper.findByCodeHash(hashCode(code));
        if (invitation == null) {
            throw new NotFoundException(ErrorCode.TRIP_INVITATION_NOT_FOUND);
        }
        LocalDateTime now = DateTimeUtil.nowUtc();
        if (invitation.getExpiresAt() != null && invitation.getExpiresAt().isBefore(now)) {
            throw new BizException(ErrorCode.TRIP_INVITATION_EXPIRED);
        }
        if (invitation.getUsedCount() != null && invitation.getMaxUses() != null
                && invitation.getUsedCount() >= invitation.getMaxUses()) {
            throw new BizException(ErrorCode.TRIP_INVITATION_USED_UP);
        }
        long ownerCount = tripMemberMapper.countEffectiveActiveOwnersByTripId(invitation.getTripId());
        if (ownerCount != 1) {
            throw new BizException(ErrorCode.TRIP_OWNER_INVARIANT_VIOLATION,
                    "该旅程当前 owner 数量异常（" + ownerCount + "），请联系客服处理");
        }
        Trip lockedTrip = tripMapper.lockByIdForUpdate(invitation.getTripId());
        if (lockedTrip == null) {
            throw new NotFoundException(ErrorCode.TRIP_INVITATION_NOT_FOUND);
        }

        TripMember existing = tripMemberMapper.findActiveByTripIdAndUserId(invitation.getTripId(), userId);
        if (existing != null) {
            return toJoinResp(existing);
        }

        TripMember any = tripMemberMapper.findAnyByTripIdAndUserId(invitation.getTripId(), userId);
        TripMember member;
        if (any != null) {
            tripMemberMapper.restoreMember(any.getId(), invitation.getRoleToGrant(),
                    invitation.getCreatedByUserId(), now);
            member = tripMemberMapper.findActiveByTripIdAndUserId(invitation.getTripId(), userId);
        } else {
            member = new TripMember();
            member.setTripId(invitation.getTripId());
            member.setUserId(userId);
            member.setRole(invitation.getRoleToGrant());
            member.setStatus(ACTIVE);
            member.setInvitedByUserId(invitation.getCreatedByUserId());
            member.setJoinedAt(now);
            tripMemberMapper.insert(member);
        }
        int incremented = invitationMapper.incrementUsedCount(invitation.getId());
        if (incremented == 0) {
            throw new BizException(ErrorCode.TRIP_INVITATION_USED_UP);
        }

        return toJoinResp(member);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TripMemberResp> listMembers(String tripId) {
        String userId = LoginUserContext.requireUserId();
        tripAccessService.requirePermission(tripId, userId, TripPermission.READ);
        List<TripMember> members = tripMemberMapper.listActiveByTripId(tripId);
        List<String> memberUserIds = members.stream()
                .map(TripMember::getUserId)
                .distinct()
                .toList();
        Map<String, User> users = memberUserIds.isEmpty()
                ? Map.of()
                : userMapper.selectBatchIds(memberUserIds).stream()
                        .collect(Collectors.toMap(User::getId, user -> user, (left, right) -> left));
        return members.stream().map(member -> toResp(member, users.get(member.getUserId()))).toList();
    }

    @Override
    @Transactional
    public TripMemberResp updateMember(String tripId, String memberId, TripMemberUpdateReq req) {
        String userId = LoginUserContext.requireUserId();
        tripAccessService.requirePermission(tripId, userId, TripPermission.MANAGE_MEMBERS);
        TripMember member = requireEffectiveMember(tripId, memberId);
        if (TripMemberRole.fromValue(member.getRole()).isOwner()) {
            throw new BizException(ErrorCode.TRIP_MEMBER_ROLE_INVALID, "owner 角色不能修改");
        }
        String role = normalizeGrantRole(req.role());
        tripMemberMapper.updateRole(tripId, memberId, role);
        TripMember updated = requireEffectiveMember(tripId, memberId);
        return toResp(updated, userMapper.selectById(updated.getUserId()));
    }

    @Override
    @Transactional
    public TripOwnershipTransferResp transferOwnership(String tripId, TripOwnershipTransferReq req) {
        String userId = LoginUserContext.requireUserId();
        Trip lockedTrip = tripMapper.lockByIdForUpdate(tripId);
        if (lockedTrip == null) {
            throw new NotFoundException(ErrorCode.TRIP_NOT_FOUND);
        }

        TripAccessService.TripAccess access =
                tripAccessService.requirePermission(tripId, userId, TripPermission.TRANSFER_OWNERSHIP);
        TripMember currentOwner = access.member();
        if (currentOwner == null || !access.role().isOwner()) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }

        String targetMemberId = req.memberId().trim();
        TripMember target = tripMemberMapper.findEffectiveActiveByIdAndTripId(targetMemberId, tripId);
        if (target == null) {
            throw new NotFoundException(ErrorCode.TRIP_MEMBER_NOT_FOUND);
        }
        if (currentOwner.getId().equals(target.getId())) {
            throw new BizException(ErrorCode.TRIP_MEMBER_ROLE_INVALID, "不能将所有权转让给自己");
        }
        if (TripMemberRole.fromValue(target.getRole()).isOwner()) {
            throw new BizException(ErrorCode.TRIP_MEMBER_ROLE_INVALID, "目标成员已经是 owner");
        }

        if (tripMemberMapper.demoteOwner(tripId, currentOwner.getId()) != 1
                || tripMemberMapper.promoteOwner(tripId, target.getId()) != 1
                || tripMapper.updateOwnerUserId(tripId, target.getUserId()) != 1) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "旅程所有权转让失败，请重试");
        }

        currentOwner.setRole(TripMemberRole.EDITOR.value());
        target.setRole(TripMemberRole.OWNER.value());
        TripAccessService.TripAccess updatedAccess = new TripAccessService.TripAccess(lockedTrip, currentOwner);
        activityLogService.record(
                tripId,
                userId,
                currentOwner.getId(),
                "TRIP_OWNERSHIP_TRANSFERRED",
                "MEMBER",
                target.getId(),
                List.of(),
                "将旅程所有权转让给了另一位成员");
        return new TripOwnershipTransferResp(
                tripId,
                currentOwner.getId(),
                target.getId(),
                target.getUserId(),
                TripAccessResp.from(updatedAccess));
    }

    @Override
    @Transactional
    public void removeMember(String tripId, String memberId) {
        String userId = LoginUserContext.requireUserId();
        tripAccessService.requirePermission(tripId, userId, TripPermission.MANAGE_MEMBERS);
        TripMember member = requireEffectiveMember(tripId, memberId);
        if (TripMemberRole.fromValue(member.getRole()).isOwner()) {
            throw new BizException(ErrorCode.TRIP_MEMBER_ROLE_INVALID, "owner 不能被移除");
        }
        if (expenseMapper.countGroupExpensesByPayerMemberId(tripId, memberId) > 0
                || expenseSplitMapper.countActiveByTripIdAndMemberId(tripId, memberId) > 0) {
            throw new BizException(ErrorCode.EXPENSE_SPLIT_INVALID,
                    "该成员仍有团队账务，请先调整或删除相关 AA 账单");
        }
        tripMemberMapper.removeMember(tripId, memberId);
        if (tripMemberMapper.findEffectiveActiveByIdAndTripId(memberId, tripId) != null) {
            throw new BizException(ErrorCode.TRIP_MEMBER_ROLE_INVALID, "成员移除失败，请重试");
        }
    }

    private TripMember requireMember(String tripId, String memberId) {
        TripMember member = tripMemberMapper.findActiveByIdAndTripId(memberId, tripId);
        if (member == null) {
            throw new NotFoundException(ErrorCode.TRIP_MEMBER_NOT_FOUND);
        }
        return member;
    }

    private TripMember requireEffectiveMember(String tripId, String memberId) {
        TripMember member = tripMemberMapper.findEffectiveActiveByIdAndTripId(memberId, tripId);
        if (member == null) {
            throw new NotFoundException(ErrorCode.TRIP_MEMBER_NOT_FOUND);
        }
        return member;
    }

    private TripInvitationJoinResp toJoinResp(TripMember member) {
        return new TripInvitationJoinResp(member.getTripId(), member.getId(), member.getRole(),
                member.getStatus(), DateTimeUtil.toInstantString(member.getJoinedAt()));
    }

    private TripMemberResp toResp(TripMember member, User user) {
        return new TripMemberResp(member.getId(), member.getTripId(), member.getUserId(),
                UserDisplaySupport.nickname(user), resolveAvatarUrl(user),
                member.getRole(), member.getStatus(), UserDisplaySupport.accountStatus(user),
                DateTimeUtil.toInstantString(member.getJoinedAt()));
    }

    private String resolveAvatarUrl(User user) {
        if (user == null || !StringUtils.hasText(user.getAvatarFileId())) {
            return null;
        }
        File file = fileMapper.selectById(user.getAvatarFileId());
        if (file == null || "DELETED".equals(file.getStatus())) {
            return null;
        }
        if (!user.getId().equals(file.getUserId())) {
            return null;
        }
        return fileUrlResolver.resolve(file);
    }

    private String normalizeGrantRole(String rawRole) {
        String value = StringUtils.hasText(rawRole) ? rawRole.trim().toLowerCase(Locale.ROOT) : "editor";
        TripMemberRole role;
        try {
            role = TripMemberRole.fromValue(value);
        } catch (IllegalArgumentException ex) {
            throw new BizException(ErrorCode.TRIP_MEMBER_ROLE_INVALID);
        }
        if (role.isOwner()) {
            throw new BizException(ErrorCode.TRIP_MEMBER_ROLE_INVALID, "不能创建 owner 邀请");
        }
        return role.value();
    }

    private String normalizeCode(String code) {
        return code.trim().toUpperCase(Locale.ROOT);
    }

    private String generateCode() {
        StringBuilder builder = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            builder.append(CODE_CHARS.charAt(RANDOM.nextInt(CODE_CHARS.length())));
        }
        return builder.toString();
    }

    static String hashCode(String code) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(code.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
