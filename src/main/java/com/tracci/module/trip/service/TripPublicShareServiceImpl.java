package com.tracci.module.trip.service;

import com.tracci.common.context.LoginUserContext;
import com.tracci.common.exception.BizException;
import com.tracci.common.exception.NotFoundException;
import com.tracci.common.response.ErrorCode;
import com.tracci.common.util.DateTimeUtil;
import com.tracci.integration.cos.FileUrlResolver;
import com.tracci.module.file.entity.File;
import com.tracci.module.file.repository.FileMapper;
import com.tracci.module.journal.repository.JournalEntryMapper;
import com.tracci.module.trip.dto.request.TripShareUpdateReq;
import com.tracci.module.trip.dto.response.PublicCoEditorInviteResp;
import com.tracci.module.trip.dto.response.PublicJourneyShareResp;
import com.tracci.module.trip.dto.response.TripCoverPhotoResp;
import com.tracci.module.trip.dto.response.TripDestinationResp;
import com.tracci.module.trip.dto.response.TripShareResp;
import com.tracci.module.trip.entity.Trip;
import com.tracci.module.trip.entity.TripInvitation;
import com.tracci.module.trip.repository.ItineraryNodeMapper;
import com.tracci.module.trip.repository.TripInvitationMapper;
import com.tracci.module.trip.repository.TripMapper;
import com.tracci.module.trip.repository.TripMemberMapper;
import com.tracci.module.trip.enums.TripPermission;
import com.tracci.module.trip.support.TripDestinationCoverProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class TripPublicShareServiceImpl implements TripPublicShareService {

    private static final String WEB_BASE_URL = "https://tracci.cn";
    private static final String STATUS_VALID = "valid";
    private static final String STATUS_INVALID = "invalid";
    private static final String STATUS_EXPIRED = "expired";
    private static final String STATUS_USED_UP = "used_up";
    private static final String STATUS_TRIP_MISMATCH = "trip_mismatch";

    private final TripMapper tripMapper;
    private final TripInvitationMapper invitationMapper;
    private final TripMemberMapper tripMemberMapper;
    private final ItineraryNodeMapper itineraryNodeMapper;
    private final JournalEntryMapper journalEntryMapper;
    private final FileMapper fileMapper;
    private final FileUrlResolver fileUrlResolver;
    private final TripDestinationCoverProvider destinationCoverProvider;
    private final TripAccessService tripAccessService;

    @Override
    @Transactional
    public TripShareResp updateShare(String tripId, TripShareUpdateReq req) {
        String userId = LoginUserContext.requireUserId();
        tripAccessService.requirePermission(tripId, userId, TripPermission.MANAGE_SHARE);
        int updated = tripMapper.updateShareEnabled(tripId, Boolean.TRUE.equals(req.shareEnabled()));
        if (updated == 0) {
            throw new NotFoundException(ErrorCode.TRIP_NOT_FOUND);
        }
        return new TripShareResp(tripId, req.shareEnabled(), journeyShareUrl(tripId));
    }

    @Override
    @Transactional(readOnly = true)
    public PublicJourneyShareResp getJourneyShare(String tripId) {
        Trip trip = requireTrip(tripId);
        if (!Boolean.TRUE.equals(trip.getShareEnabled())) {
            throw new NotFoundException(ErrorCode.TRIP_NOT_FOUND);
        }
        return toJourneyShareResp(trip, journeyShareUrl(tripId));
    }

    @Override
    @Transactional(readOnly = true)
    public PublicCoEditorInviteResp validateCoEditorInvite(String tripId, String inviteCode) {
        String code = normalizeCode(inviteCode);
        Trip trip = tripMapper.selectById(tripId);
        PublicJourneyShareResp tripResp = trip == null ? null : toJourneyShareResp(trip, coEditorUrl(tripId, code));
        String status = inviteStatus(tripId, code);
        TripInvitation invitation = STATUS_INVALID.equals(status)
                ? null
                : invitationMapper.findByCodeHash(TripCollaborationServiceImpl.hashCode(code));
        boolean valid = STATUS_VALID.equals(status);
        String role = invitation == null ? null : invitation.getRoleToGrant();
        String expiresAt = invitation == null ? null : DateTimeUtil.toInstantString(invitation.getExpiresAt());
        String title = trip == null ? "趣溪共同编辑邀请" : "邀请你一起编辑：" + trip.getTitle();
        String description = valid
                ? "打开趣溪 App 或小程序，输入邀请码 " + code + " 加入共同编辑。"
                : "该共同编辑邀请当前不可用，请联系邀请人重新发送。";
        String imageUrl = tripResp == null || tripResp.coverPhoto() == null ? null : tripResp.coverPhoto().url();
        PublicCoEditorInviteResp.Meta meta = new PublicCoEditorInviteResp.Meta(
                title, description, imageUrl, coEditorUrl(tripId, code));
        return new PublicCoEditorInviteResp(tripId, code, valid, status, role, expiresAt, tripResp, meta);
    }

    private String inviteStatus(String tripId, String code) {
        if (!StringUtils.hasText(tripId) || !StringUtils.hasText(code)) {
            return STATUS_INVALID;
        }
        TripInvitation invitation = invitationMapper.findByCodeHash(TripCollaborationServiceImpl.hashCode(code));
        if (invitation == null) {
            return STATUS_INVALID;
        }
        if (!tripId.equals(invitation.getTripId())) {
            return STATUS_TRIP_MISMATCH;
        }
        LocalDateTime now = DateTimeUtil.nowUtc();
        if (invitation.getExpiresAt() != null && invitation.getExpiresAt().isBefore(now)) {
            return STATUS_EXPIRED;
        }
        if (invitation.getUsedCount() != null && invitation.getMaxUses() != null
                && invitation.getUsedCount() >= invitation.getMaxUses()) {
            return STATUS_USED_UP;
        }
        return STATUS_VALID;
    }

    private PublicJourneyShareResp toJourneyShareResp(Trip trip, String url) {
        TripCoverPhotoResp cover = resolveCover(trip);
        long placeCount = itineraryNodeMapper.countDistinctPlacesByTripId(trip.getId());
        long itineraryCount = itineraryNodeMapper.countByTripId(trip.getId());
        long journalCount = journalEntryMapper.countPublicByTripId(trip.getId());
        long memberCount = tripMemberMapper.countEffectiveActiveByTripId(trip.getId());
        String description = description(trip, placeCount);
        PublicJourneyShareResp.Meta meta = new PublicJourneyShareResp.Meta(
                trip.getTitle(),
                description,
                cover == null ? null : cover.url(),
                url);
        return new PublicJourneyShareResp(
                trip.getId(),
                trip.getTitle(),
                trip.getStartDate() == null ? null : trip.getStartDate().toString(),
                trip.getEndDate() == null ? null : trip.getEndDate().toString(),
                totalDays(trip),
                toDestinationResp(trip),
                cover,
                placeCount,
                itineraryCount,
                journalCount,
                memberCount,
                url,
                meta);
    }

    private Trip requireTrip(String tripId) {
        Trip trip = tripMapper.selectById(tripId);
        if (trip == null) {
            throw new NotFoundException(ErrorCode.TRIP_NOT_FOUND);
        }
        return trip;
    }

    private TripCoverPhotoResp resolveCover(Trip trip) {
        if (StringUtils.hasText(trip.getCoverFileId())) {
            File file = fileMapper.selectById(trip.getCoverFileId());
            if (file != null && "UPLOADED".equals(file.getStatus())) {
                var urls = fileUrlResolver.resolveUrls(file);
                if (StringUtils.hasText(urls.url())) {
                    return new TripCoverPhotoResp(file.getId(), urls.url(), urls.thumbnailUrl(), urls.previewUrl(),
                            urls.exportUrl(), urls.storyPortraitUrl(), urls.storyCardUrl(), urls.squareUrl(),
                            urls.watermarkedUrl(), file.getWidth(), file.getHeight());
                }
            }
        }
        return destinationCoverProvider.resolveTripCover(trip).orElse(null);
    }

    private TripDestinationResp toDestinationResp(Trip trip) {
        if (!StringUtils.hasText(trip.getDestinationName())) {
            return null;
        }
        return new TripDestinationResp(
                trip.getDestinationName(),
                trip.getDestinationAdcode(),
                trip.getDestinationCityCode(),
                trip.getDestinationProvinceName(),
                trip.getDestinationCityName(),
                trip.getDestinationDistrictName(),
                trip.getDestinationLatitude(),
                trip.getDestinationLongitude(),
                trip.getDestinationProvider(),
                trip.getDestinationProviderPlaceId(),
                trip.getDestinationCountry(),
                null);
    }

    private long totalDays(Trip trip) {
        if (trip.getStartDate() == null || trip.getEndDate() == null || trip.getEndDate().isBefore(trip.getStartDate())) {
            return 0;
        }
        return ChronoUnit.DAYS.between(trip.getStartDate(), trip.getEndDate()) + 1;
    }

    private String description(Trip trip, long placeCount) {
        String destination = StringUtils.hasText(trip.getDestinationName()) ? trip.getDestinationName() : "旅行";
        long days = totalDays(trip);
        if (days > 0 && placeCount > 0) {
            return destination + "，" + days + " 天，" + placeCount + " 个地点。";
        }
        if (days > 0) {
            return destination + "，" + days + " 天。";
        }
        return destination + "行程分享。";
    }

    private String normalizeCode(String code) {
        if (!StringUtils.hasText(code)) {
            throw new BizException(ErrorCode.AUTH_PARAM_INVALID, "code 不能为空");
        }
        return code.trim().toUpperCase(Locale.ROOT);
    }

    private String journeyShareUrl(String tripId) {
        return WEB_BASE_URL + "/journey/detail/" + tripId + "?source=copyLink";
    }

    private String coEditorUrl(String tripId, String code) {
        return WEB_BASE_URL + "/co-editor?id=" + tripId + "&code=" + code;
    }
}
