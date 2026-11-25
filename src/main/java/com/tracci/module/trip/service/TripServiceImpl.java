package com.tracci.module.trip.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tracci.common.context.LoginUserContext;
import com.tracci.common.exception.BizException;
import com.tracci.common.exception.ForbiddenException;
import com.tracci.common.exception.NotFoundException;
import com.tracci.common.response.ErrorCode;
import com.tracci.common.response.PageResponse;
import com.tracci.integration.wechat.WechatContentSecurityClient;
import com.tracci.module.auth.entity.UserAuthIdentity;
import com.tracci.module.auth.enums.AuthProvider;
import com.tracci.module.auth.repository.UserAuthIdentityMapper;
import com.tracci.module.trip.dto.request.TripCreateReq;
import com.tracci.common.enums.LocationProvider;
import com.tracci.common.util.DateTimeUtil;
import com.tracci.module.trip.dto.request.TripDestinationReq;
import com.tracci.module.trip.dto.request.TripUpdateReq;
import com.tracci.module.trip.dto.response.TripCreateResp;
import com.tracci.module.trip.dto.response.TripActivityLogResp;
import com.tracci.module.trip.dto.response.TripAccessResp;
import com.tracci.module.trip.dto.response.TripCoverPhotoResp;
import com.tracci.module.trip.dto.response.TripDestinationResp;
import com.tracci.module.trip.dto.response.TripDetailResp;
import com.tracci.module.trip.dto.response.TripListResp;
import com.tracci.integration.cos.FileUrlResolver;
import com.tracci.module.trip.support.TripDestinationCoverProvider;
import com.tracci.module.file.entity.File;
import com.tracci.module.file.repository.FileMapper;
import com.tracci.module.trip.entity.Trip;
import com.tracci.module.trip.entity.TripDay;
import com.tracci.module.trip.entity.TripMember;
import com.tracci.module.trip.enums.TripMemberRole;
import com.tracci.module.trip.enums.TripPermission;
import com.tracci.module.trip.enums.TripStatus;
import com.tracci.module.expense.repository.ExpenseMapper;
import com.tracci.module.journal.repository.JournalEntryMapper;
import com.tracci.module.journal.repository.JournalPhotoMapper;
import com.tracci.module.trip.repository.TripDayMapper;
import com.tracci.module.trip.repository.ItineraryNodeMapper;
import com.tracci.module.trip.repository.TripMapper;
import com.tracci.module.trip.repository.TripMemberMapper;
import com.tracci.module.place.entity.Place;
import com.tracci.module.place.enums.PlaceCategory;
import com.tracci.module.place.enums.PlaceScope;
import com.tracci.module.place.service.PlaceService;
import com.tracci.module.savedplace.dto.request.LocationCandidate;
import com.tracci.module.savedplace.service.SavedPlaceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TripServiceImpl implements TripService {

    private static final String DEFAULT_BASE_CURRENCY = "CNY";
    private static final String ZERO_CNY = "0.00";

    private final TripMapper tripMapper;
    private final TripDayMapper tripDayMapper;
    private final TripMemberMapper tripMemberMapper;
    private final ItineraryNodeMapper itineraryNodeMapper;
    private final ExpenseMapper expenseMapper;
    private final JournalEntryMapper journalEntryMapper;
    private final JournalPhotoMapper journalPhotoMapper;
    private final FileMapper fileMapper;
    private final FileUrlResolver fileUrlResolver;
    private final TripDestinationCoverProvider destinationCoverProvider;
    private final UserAuthIdentityMapper authIdentityMapper;
    private final WechatContentSecurityClient wechatContentSecurityClient;
    private final TripAccessService tripAccessService;
    private final TripActivityLogService activityLogService;
    private final ItineraryService itineraryService;
    private final SavedPlaceService savedPlaceService;
    private final PlaceService placeService;

    @Override
    @Transactional
    public TripCreateResp createTrip(TripCreateReq req) {
        String userId = LoginUserContext.requireUserId();
        LocalDate startDate = requireDate(req.startDate(), "出发日期不能为空");
        LocalDate endDate = requireDate(req.endDate(), "返回日期不能为空");
        validateDateRange(startDate, endDate);

        // SavedPlace 优先：如果指定了 destinationSavedPlaceId，从 Place 读取目的地快照
        String savedPlaceId = normalizeOptional(req.destinationSavedPlaceId());
        Place destinationPlace = null;
        if (StringUtils.hasText(savedPlaceId)) {
            destinationPlace = savedPlaceService.resolveForTripDestination(userId, savedPlaceId);
            checkPublishContentSafety(userId, req.title(), destinationPlace);
        } else {
            checkPublishContentSafety(userId, req.title(), req.destination());
            if (req.destination() != null && hasDestinationContent(req.destination())) {
                destinationPlace = placeService.resolveOrCreatePlace(
                        toLocationCandidate(req.destination()),
                        PlaceScope.DESTINATION.name(),
                        PlaceCategory.CITY.name());
            }
        }

        Trip trip = new Trip();
        trip.setUserId(userId);
        trip.setTitle(normalizeTitle(req.title()));
        trip.setStartDate(startDate);
        trip.setEndDate(endDate);
        trip.setCoverFileId(normalizeOptional(req.coverFileId()));
        trip.setBaseCurrency(normalizeCurrency(req.baseCurrency()));
        if (StringUtils.hasText(savedPlaceId)) {
            applyDestinationFromPlace(trip, destinationPlace);
        } else {
            applyDestinationForCreate(trip, req.destination(), destinationPlace);
        }
        trip.setStatus(calculateStatus(startDate, endDate).value());
        trip.setShareEnabled(false);
        File coverFile = validateCoverFileForCreate(trip.getCoverFileId(), userId);
        tripMapper.insert(trip);
        bindCoverFile(coverFile, trip.getId());

        // 写入 SavedPlace usage（Trip 已插入，有 tripId）
        if (StringUtils.hasText(savedPlaceId)) {
            savedPlaceService.recordTripUsage(userId, savedPlaceId, trip.getId());
        }

        createOrRestoreTripDays(trip.getId(), userId, startDate, endDate);
        createOwnerMember(trip.getId(), userId);
        return toCreateResp(trip);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<TripListResp> listTrips(int page, int pageSize, String status, String keyword) {
        String userId = LoginUserContext.requireUserId();
        int safePage = Math.max(page, 1);
        int safePageSize = Math.min(Math.max(pageSize, 1), 100);
        LocalDate today = DateTimeUtil.todayBusiness();

        String normalizedStatus = normalizeStatusFilter(status);
        String normalizedKeyword = normalizeKeyword(keyword);
        Page<Trip> result = tripMapper.pageMemberTrips(
                new Page<>(safePage, safePageSize), userId, normalizedStatus, normalizedKeyword, today);
        if (result == null) {
            QueryWrapper<Trip> query = new QueryWrapper<Trip>()
                    .eq("user_id", userId)
                    .orderByDesc("created_at");
            applyStatusFilter(query, status, today);
            applyKeywordFilter(query, keyword);
            result = tripMapper.selectPage(new Page<>(safePage, safePageSize), query);
        }
        TripListContext context = buildTripListContext(result.getRecords(), userId);
        List<TripListResp> list = result.getRecords().stream()
                .map(trip -> toListResp(trip, context))
                .toList();
        return PageResponse.of(list, result.getTotal(), safePage, safePageSize);
    }

    @Override
    @Transactional(readOnly = true)
    public TripDetailResp getTrip(String tripId) {
        String userId = LoginUserContext.requireUserId();
        TripAccessService.TripAccess access =
                tripAccessService.requirePermission(tripId, userId, TripPermission.READ);
        return toDetailResp(access.trip(), userId, access);
    }

    @Override
    @Transactional
    public TripDetailResp updateTrip(String tripId, TripUpdateReq req) {
        String userId = LoginUserContext.requireUserId();
        TripAccessService.TripAccess access =
                tripAccessService.requirePermission(tripId, userId, TripPermission.UPDATE_TRIP);
        Trip trip = access.trip();
        checkPublishContentSafety(userId, req.title(), req.destination());
        String oldTitle = trip.getTitle();
        String oldCoverFileId = trip.getCoverFileId();
        String oldBaseCurrency = trip.getBaseCurrency();
        String oldStartDate = trip.getStartDate().toString();
        String oldEndDate = trip.getEndDate().toString();

        LocalDate startDate = req.startDate() != null ? req.startDate() : trip.getStartDate();
        LocalDate endDate = req.endDate() != null ? req.endDate() : trip.getEndDate();
        validateDateRange(startDate, endDate);

        boolean dateChanged = !startDate.equals(trip.getStartDate()) || !endDate.equals(trip.getEndDate());
        UpdateWrapper<Trip> update = new UpdateWrapper<Trip>()
                .eq("id", tripId)
                .eq("user_id", userId);

        if (req.title() != null) {
            String title = normalizeTitle(req.title());
            trip.setTitle(title);
            update.set("title", title);
        }
        if (req.coverFileId() != null) {
            File coverFile = validateCoverFileForUpdate(normalizeOptional(req.coverFileId()), userId, tripId);
            String coverFileId = coverFile != null ? coverFile.getId() : null;
            trip.setCoverFileId(coverFileId);
            update.set("cover_file_id", coverFileId);
            bindCoverFile(coverFile, tripId);
        }
        if (req.baseCurrency() != null) {
            String baseCurrency = normalizeCurrency(req.baseCurrency());
            trip.setBaseCurrency(baseCurrency);
            update.set("base_currency", baseCurrency);
        }
        if (req.destination() != null) {
            applyDestinationForUpdate(trip, req.destination(), update);
        }
        LocalDate today = DateTimeUtil.todayBusiness();
        TripStatus previousStatus = TripStatus.calculate(today, trip.getStartDate(), trip.getEndDate());
        boolean statusReopenConfirmed = false;
        if (dateChanged) {
            // 状态变更守卫：已结束旅程的日期修改需客户端明确确认，防止状态被意外重开
            TripStatus newStatus = TripStatus.calculate(today, startDate, endDate);
            if (previousStatus == TripStatus.COMPLETED && newStatus != TripStatus.COMPLETED
                    && !Boolean.TRUE.equals(req.statusChangeConfirmed())) {
                throw new BizException(ErrorCode.TRIP_STATUS_CHANGE_REQUIRES_CONFIRM);
            }
            statusReopenConfirmed = previousStatus == TripStatus.COMPLETED
                    && newStatus != TripStatus.COMPLETED
                    && Boolean.TRUE.equals(req.statusChangeConfirmed());
            trip.setStartDate(startDate);
            trip.setEndDate(endDate);
            update.set("start_date", startDate);
            update.set("end_date", endDate);
        }

        String status = calculateStatus(startDate, endDate).value();
        trip.setStatus(status);
        update.set("status", status);
        int updated = tripMapper.update(null, update);
        if (updated == 0) {
            throw new NotFoundException(ErrorCode.TRIP_NOT_FOUND);
        }

        if (dateChanged) {
            syncTripDays(tripId, userId, startDate, endDate);
        }
        recordActivity(tripId, userId, memberId(access), "TRIP_UPDATED", "TRIP", tripId,
                changes(
                        change("title", oldTitle, trip.getTitle()),
                        change("coverFileId", oldCoverFileId, trip.getCoverFileId()),
                        change("baseCurrency", oldBaseCurrency, trip.getBaseCurrency()),
                        change("startDate", oldStartDate, trip.getStartDate().toString()),
                        change("endDate", oldEndDate, trip.getEndDate().toString()),
                        // 审计：状态重开与用户确认标记（statusChangeConfirmed 非授权位，仅 UX+审计）
                        change("status", previousStatus.value(), status),
                        statusReopenConfirmed ? change("statusChangeConfirmed", null, "true") : null),
                statusReopenConfirmed ? "确认后重开了已结束旅程" : "更新了旅行信息");
        return toDetailResp(trip, userId, access);
    }

    @Override
    @Transactional
    public void deleteTrip(String tripId) {
        String userId = LoginUserContext.requireUserId();
        TripAccessService.TripAccess access =
                tripAccessService.requirePermission(tripId, userId, TripPermission.DELETE_TRIP);

        itineraryNodeMapper.softDeleteAllByTripId(tripId);
        itineraryNodeMapper.softDeleteByTripId(tripId, userId);
        tripDayMapper.softDeleteAllByTripId(tripId);
        tripDayMapper.softDeleteByTripId(tripId, userId);
        tripMemberMapper.softDeleteAllByTripId(tripId);
        tripMemberMapper.softDeleteByTripId(tripId, userId);
        expenseMapper.softDeleteAllByTripId(tripId);
        expenseMapper.softDeleteByTripId(tripId, userId);
        journalPhotoMapper.softDeleteAllByTripId(tripId);
        journalPhotoMapper.softDeleteByTripId(tripId, userId);
        journalEntryMapper.softDeleteAllByTripId(tripId);
        journalEntryMapper.softDeleteByTripId(tripId, userId);
        fileMapper.softDeleteByBusiness(userId, "JOURNAL_PHOTO", tripId);
        fileMapper.softDeleteByBusiness(userId, "TRIP_COVER", tripId);
        int deleted = tripMapper.delete(new UpdateWrapper<Trip>()
                .eq("id", tripId)
                .eq("user_id", userId));
        if (deleted == 0) {
            throw new NotFoundException(ErrorCode.TRIP_NOT_FOUND);
        }
        recordActivity(tripId, userId, memberId(access), "TRIP_DELETED", "TRIP", tripId, List.of(), "删除了旅行");
    }

    private String normalizeStatusFilter(String rawStatus) {
        if (!StringUtils.hasText(rawStatus)) {
            return null;
        }
        try {
            return TripStatus.fromValue(rawStatus.trim()).value();
        } catch (IllegalArgumentException ex) {
            throw new BizException(ErrorCode.AUTH_PARAM_INVALID, "status 仅支持 planning / ongoing / completed");
        }
    }

    private String normalizeKeyword(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return null;
        }
        String value = keyword.trim();
        if (value.length() > 100) {
            throw new BizException(ErrorCode.AUTH_PARAM_INVALID, "keyword 长度不能超过 100");
        }
        return value;
    }

    private void applyStatusFilter(QueryWrapper<Trip> query, String rawStatus, LocalDate today) {
        if (!StringUtils.hasText(rawStatus)) {
            return;
        }
        TripStatus status;
        try {
            status = TripStatus.fromValue(rawStatus.trim());
        } catch (IllegalArgumentException ex) {
            throw new BizException(ErrorCode.AUTH_PARAM_INVALID, "status 仅支持 planning / ongoing / completed");
        }
        switch (status) {
            case PLANNING -> query.gt("start_date", today);
            case ONGOING -> query.le("start_date", today).ge("end_date", today);
            case COMPLETED -> query.lt("end_date", today);
        }
    }

    private void applyKeywordFilter(QueryWrapper<Trip> query, String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return;
        }
        String value = keyword.trim();
        if (value.length() > 100) {
            throw new BizException(ErrorCode.AUTH_PARAM_INVALID, "keyword 长度不能超过 100");
        }
        query.like("title", value);
    }

    private Trip getExistingTrip(String tripId) {
        Trip trip = tripMapper.selectById(tripId);
        if (trip == null) {
            throw new NotFoundException(ErrorCode.TRIP_NOT_FOUND);
        }
        return trip;
    }

    private String memberId(TripAccessService.TripAccess access) {
        return access != null && access.member() != null ? access.member().getId() : null;
    }

    private void recordActivity(String tripId, String userId, String memberId, String action,
                                String targetType, String targetId,
                                List<TripActivityLogResp.FieldChange> changes, String summary) {
        if (activityLogService != null) {
            activityLogService.record(tripId, userId, memberId, action, targetType, targetId, changes, summary);
        }
    }

    private File validateCoverFileForCreate(String coverFileId, String userId) {
        if (!StringUtils.hasText(coverFileId)) {
            return null;
        }
        return validateCoverFile(coverFileId, userId, null);
    }

    private File validateCoverFileForUpdate(String coverFileId, String userId, String tripId) {
        if (!StringUtils.hasText(coverFileId)) {
            return null;
        }
        return validateCoverFile(coverFileId, userId, tripId);
    }

    private File validateCoverFile(String coverFileId, String userId, String tripId) {
        File file = fileMapper.selectById(coverFileId);
        if (file == null || "DELETED".equals(file.getStatus())) {
            throw new NotFoundException(ErrorCode.FILE_NOT_FOUND);
        }
        if (!userId.equals(file.getUserId())) {
            throw new ForbiddenException();
        }
        if (!"UPLOADED".equals(file.getStatus())) {
            throw new BizException(ErrorCode.FILE_NOT_UPLOADED);
        }
        if (!StringUtils.hasText(file.getContentType()) || !file.getContentType().startsWith("image/")) {
            throw new BizException(ErrorCode.FILE_NOT_IMAGE);
        }
        if (!"TRIP_COVER".equals(file.getBusinessType())) {
            throw new BizException(ErrorCode.AUTH_PARAM_INVALID, "coverFileId 必须是 TRIP_COVER 文件");
        }
        if (!StringUtils.hasText(tripId) && StringUtils.hasText(file.getBusinessId())) {
            throw new ForbiddenException();
        }
        if (StringUtils.hasText(tripId) && StringUtils.hasText(file.getBusinessId()) && !tripId.equals(file.getBusinessId())) {
            throw new ForbiddenException();
        }
        return file;
    }

    private void bindCoverFile(File coverFile, String tripId) {
        if (coverFile == null || tripId.equals(coverFile.getBusinessId())) {
            return;
        }
        coverFile.setBusinessId(tripId);
        fileMapper.updateById(coverFile);
    }

    private void createOwnerMember(String tripId, String userId) {
        TripMember member = new TripMember();
        member.setTripId(tripId);
        member.setUserId(userId);
        member.setRole(TripMemberRole.OWNER.value());
        member.setJoinedAt(DateTimeUtil.nowUtc());
        tripMemberMapper.insert(member);
    }

    private void syncTripDays(String tripId, String userId, LocalDate startDate, LocalDate endDate) {
        createOrRestoreTripDays(tripId, userId, startDate, endDate);
        itineraryNodeMapper.softDeleteAfterDayIndex(tripId, userId, totalDays(startDate, endDate));
        tripDayMapper.softDeleteAfterDayIndex(tripId, userId, totalDays(startDate, endDate));
        // Phase 3：改期后裁剪 hotel stay（投影越界 / checkOutDate 与行程天错位）
        if (itineraryService != null) {
            itineraryService.reconcileHotelStaysAfterTripDateChange(tripId);
        }
    }

    private void createOrRestoreTripDays(String tripId, String userId, LocalDate startDate, LocalDate endDate) {
        int days = totalDays(startDate, endDate);
        for (int dayIndex = 1; dayIndex <= days; dayIndex++) {
            LocalDate date = startDate.plusDays(dayIndex - 1L);
            int updated = tripDayMapper.restoreOrUpdateDay(tripId, userId, dayIndex, date);
            if (updated == 0) {
                TripDay day = new TripDay();
                day.setTripId(tripId);
                day.setUserId(userId);
                day.setDayIndex(dayIndex);
                day.setDate(date);
                tripDayMapper.insert(day);
            }
        }
    }

    private String normalizeTitle(String title) {
        if (!StringUtils.hasText(title)) {
            throw new BizException(ErrorCode.AUTH_PARAM_INVALID, "title 不能为空");
        }
        String value = title.trim();
        if (value.length() > 100) {
            throw new BizException(ErrorCode.AUTH_PARAM_INVALID, "title 长度不能超过 100");
        }
        return value;
    }

    private String normalizeCurrency(String currency) {
        if (!StringUtils.hasText(currency)) {
            return DEFAULT_BASE_CURRENCY;
        }
        String value = currency.trim().toUpperCase(Locale.ROOT);
        if (!value.matches("[A-Z]{3}")) {
            throw new BizException(ErrorCode.AUTH_PARAM_INVALID, "baseCurrency 必须是 3 位 ISO 4217 代码");
        }
        return value;
    }

    private String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return StringUtils.hasText(trimmed) ? trimmed : null;
    }

    private void checkPublishContentSafety(String userId, String title, TripDestinationReq destination) {
        String openid = findWechatMiniOpenid(userId);
        if (!StringUtils.hasText(openid)) {
            return;
        }
        String content = buildContentSecurityText(title, destination);
        if (StringUtils.hasText(content)) {
            wechatContentSecurityClient.checkText(openid, content);
        }
    }

    private void checkPublishContentSafety(String userId, String title, Place destinationPlace) {
        String openid = findWechatMiniOpenid(userId);
        if (!StringUtils.hasText(openid)) {
            return;
        }
        StringBuilder builder = new StringBuilder();
        appendContentSecurityText(builder, title);
        if (destinationPlace != null) {
            appendContentSecurityText(builder, destinationPlace.getCanonicalName());
            appendContentSecurityText(builder, destinationPlace.getCityName());
            appendContentSecurityText(builder, destinationPlace.getCountry());
        }
        String content = builder.toString();
        if (StringUtils.hasText(content)) {
            wechatContentSecurityClient.checkText(openid, content);
        }
    }

    private String findWechatMiniOpenid(String userId) {
        UserAuthIdentity identity = authIdentityMapper.selectOne(new LambdaQueryWrapper<UserAuthIdentity>()
                .eq(UserAuthIdentity::getUserId, userId)
                .eq(UserAuthIdentity::getProvider, AuthProvider.WECHAT_MINI.name())
                .last("limit 1"));
        return identity != null ? identity.getProviderUserId() : null;
    }

    private String buildContentSecurityText(String title, TripDestinationReq destination) {
        StringBuilder builder = new StringBuilder();
        appendContentSecurityText(builder, title);
        if (destination != null) {
            appendContentSecurityText(builder, destination.name());
            appendContentSecurityText(builder, destination.provinceName());
            appendContentSecurityText(builder, destination.cityName());
            appendContentSecurityText(builder, destination.districtName());
            appendContentSecurityText(builder, destination.country());
        }
        return builder.toString();
    }

    private void appendContentSecurityText(StringBuilder builder, String value) {
        String normalized = normalizeOptional(value);
        if (normalized == null) {
            return;
        }
        if (!builder.isEmpty()) {
            builder.append('\n');
        }
        builder.append(normalized);
    }

    private LocalDate requireDate(LocalDate date, String message) {
        if (date == null) {
            throw new BizException(ErrorCode.AUTH_PARAM_INVALID, message);
        }
        return date;
    }

    private void validateDateRange(LocalDate startDate, LocalDate endDate) {
        if (endDate.isBefore(startDate)) {
            throw new BizException(ErrorCode.AUTH_PARAM_INVALID, "endDate 必须大于等于 startDate");
        }
        long days = ChronoUnit.DAYS.between(startDate, endDate) + 1;
        if (days > Short.MAX_VALUE) {
            throw new BizException(ErrorCode.AUTH_PARAM_INVALID, "旅行天数过长");
        }
    }

    private TripStatus calculateStatus(LocalDate startDate, LocalDate endDate) {
        return TripStatus.calculate(DateTimeUtil.todayBusiness(), startDate, endDate);
    }

    private String resolveStatus(Trip trip) {
        return TripStatus.resolveValue(DateTimeUtil.todayBusiness(), trip.getStartDate(), trip.getEndDate(), trip.getStatus());
    }

    private int totalDays(LocalDate startDate, LocalDate endDate) {
        return Math.toIntExact(ChronoUnit.DAYS.between(startDate, endDate) + 1);
    }

    private TripCreateResp toCreateResp(Trip trip) {
        return new TripCreateResp(
                trip.getId(),
                trip.getTitle(),
                trip.getStartDate().toString(),
                trip.getEndDate().toString(),
                calculateStatus(trip.getStartDate(), trip.getEndDate()).value(),
                totalDays(trip.getStartDate(), trip.getEndDate()),
                toDestinationResp(trip),
                toCoverPhoto(trip),
                trip.getBaseCurrency(),
                DateTimeUtil.toInstantString(trip.getCreatedAt())
        );
    }

    private TripListResp toListResp(Trip trip, TripListContext context) {
        TripStats stats = context.statsByTripId().getOrDefault(trip.getId(), zeroStats());
        return new TripListResp(
                trip.getId(),
                trip.getTitle(),
                trip.getStartDate().toString(),
                trip.getEndDate().toString(),
                resolveStatus(trip),
                totalDays(trip.getStartDate(), trip.getEndDate()),
                toDestinationResp(trip),
                toCoverPhoto(trip, StringUtils.hasText(trip.getCoverFileId())
                        ? context.filesById().get(trip.getCoverFileId())
                        : null),
                stats.totalCny(),
                stats.nodeCount(),
                stats.journalCount()
        );
    }

    private TripDetailResp toDetailResp(
            Trip trip,
            String userId,
            TripAccessService.TripAccess access) {
        TripStats stats = defaultStats(trip, userId);
        return new TripDetailResp(
                trip.getId(),
                trip.getTitle(),
                trip.getStartDate().toString(),
                trip.getEndDate().toString(),
                resolveStatus(trip),
                totalDays(trip.getStartDate(), trip.getEndDate()),
                toDestinationResp(trip),
                toCoverPhoto(trip),
                trip.getBaseCurrency(),
                stats.totalCny(),
                stats.nodeCount(),
                stats.journalCount(),
                TripAccessResp.from(access),
                DateTimeUtil.toInstantString(trip.getCreatedAt()),
                DateTimeUtil.toInstantString(trip.getUpdatedAt())
        );
    }

    private void applyDestinationForCreate(
            Trip trip, TripDestinationReq destination, Place destinationPlace) {
        if (destination == null || !hasDestinationContent(destination)) {
            return;
        }
        setDestinationFields(trip, destination);
        trip.setDestinationPlaceId(destinationPlace != null ? destinationPlace.getId() : null);
    }

    /**
     * 从 Place 读取目的地快照写入 Trip。
     * 解析 destination_key 回填 adcode/cityCode，便于后续 POI 目的地匹配。
     */
    private void applyDestinationFromPlace(Trip trip, Place place) {
        trip.setDestinationName(place.getCanonicalName());
        applyDestinationKeyFields(trip, place.getDestinationKey());
        trip.setDestinationProvinceName(null);
        trip.setDestinationCityName(place.getCityName());
        trip.setDestinationDistrictName(null);
        trip.setDestinationLatitude(place.getLatitude());
        trip.setDestinationLongitude(place.getLongitude());
        trip.setDestinationProvider(null);
        trip.setDestinationProviderPlaceId(null);
        trip.setDestinationCountry(place.getCountry());
        trip.setDestinationPlaceId(place.getId());
    }

    private void applyDestinationKeyFields(Trip trip, String destinationKey) {
        trip.setDestinationAdcode(null);
        trip.setDestinationCityCode(null);
        if (!StringUtils.hasText(destinationKey)) {
            return;
        }
        if (destinationKey.startsWith("adcode:")) {
            trip.setDestinationAdcode(destinationKey.substring("adcode:".length()));
        } else if (destinationKey.startsWith("cityCode:")) {
            trip.setDestinationCityCode(destinationKey.substring("cityCode:".length()));
        }
    }

    private void applyDestinationForUpdate(
            Trip trip,
            TripDestinationReq destination,
            UpdateWrapper<Trip> update) {
        if (!hasDestinationContent(destination)) {
            clearDestinationFields(trip);
        } else {
            setDestinationFields(trip, destination);
            Place place = placeService.resolveOrCreatePlace(
                    toLocationCandidate(destination),
                    PlaceScope.DESTINATION.name(),
                    PlaceCategory.CITY.name());
            trip.setDestinationPlaceId(place.getId());
        }
        update.set("destination_name", trip.getDestinationName());
        update.set("destination_adcode", trip.getDestinationAdcode());
        update.set("destination_city_code", trip.getDestinationCityCode());
        update.set("destination_province_name", trip.getDestinationProvinceName());
        update.set("destination_city_name", trip.getDestinationCityName());
        update.set("destination_district_name", trip.getDestinationDistrictName());
        update.set("destination_latitude", trip.getDestinationLatitude());
        update.set("destination_longitude", trip.getDestinationLongitude());
        update.set("destination_provider", trip.getDestinationProvider());
        update.set("destination_provider_place_id", trip.getDestinationProviderPlaceId());
        update.set("destination_country", trip.getDestinationCountry());
        update.set("destination_place_id", trip.getDestinationPlaceId());
    }

    private boolean hasDestinationContent(TripDestinationReq destination) {
        return StringUtils.hasText(destination.name())
                || StringUtils.hasText(destination.adcode())
                || StringUtils.hasText(destination.cityCode())
                || StringUtils.hasText(destination.provinceName())
                || StringUtils.hasText(destination.cityName())
                || StringUtils.hasText(destination.districtName())
                || destination.latitude() != null
                || destination.longitude() != null
                || StringUtils.hasText(destination.provider())
                || StringUtils.hasText(destination.providerPlaceId())
                || StringUtils.hasText(destination.country());
    }

    private void setDestinationFields(Trip trip, TripDestinationReq destination) {
        String name = normalizeOptional(destination.name());
        if (!StringUtils.hasText(name)) {
            throw new BizException(ErrorCode.AUTH_PARAM_INVALID, "destination.name 不能为空");
        }
        trip.setDestinationName(name);
        trip.setDestinationAdcode(normalizeOptional(destination.adcode()));
        trip.setDestinationCityCode(normalizeOptional(destination.cityCode()));
        trip.setDestinationProvinceName(normalizeOptional(destination.provinceName()));
        trip.setDestinationCityName(normalizeOptional(destination.cityName()));
        trip.setDestinationDistrictName(normalizeOptional(destination.districtName()));
        trip.setDestinationLatitude(destination.latitude());
        trip.setDestinationLongitude(destination.longitude());
        trip.setDestinationProvider(normalizeDestinationProvider(destination.provider()));
        trip.setDestinationProviderPlaceId(normalizeOptional(destination.providerPlaceId()));
        trip.setDestinationCountry(normalizeOptional(destination.country()));
    }

    private String normalizeDestinationProvider(String provider) {
        if (!StringUtils.hasText(provider)) {
            return LocationProvider.MANUAL.name();
        }
        String normalized = provider.trim().toUpperCase();
        try {
            return LocationProvider.valueOf(normalized).name();
        } catch (IllegalArgumentException ex) {
            throw new BizException(ErrorCode.AUTH_PARAM_INVALID, "destination.provider 不支持");
        }
    }

    private LocationCandidate toLocationCandidate(TripDestinationReq destination) {
        return new LocationCandidate(
                destination.name(),
                null,
                destination.provider(),
                destination.providerPlaceId(),
                destination.country(),
                destination.cityName(),
                destination.latitude(),
                destination.longitude(),
                destination.adcode(),
                destination.cityCode()
        );
    }

    private void clearDestinationFields(Trip trip) {
        trip.setDestinationName(null);
        trip.setDestinationAdcode(null);
        trip.setDestinationCityCode(null);
        trip.setDestinationProvinceName(null);
        trip.setDestinationCityName(null);
        trip.setDestinationDistrictName(null);
        trip.setDestinationLatitude(null);
        trip.setDestinationLongitude(null);
        trip.setDestinationProvider(null);
        trip.setDestinationProviderPlaceId(null);
        trip.setDestinationCountry(null);
        trip.setDestinationPlaceId(null);
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
                trip.getDestinationPlaceId()
        );
    }

    private TripCoverPhotoResp toCoverPhoto(Trip trip) {
        if (!StringUtils.hasText(trip.getCoverFileId())) {
            return destinationCoverProvider.resolveTripCover(trip).orElse(null);
        }
        File file = fileMapper.selectById(trip.getCoverFileId());
        return toCoverPhoto(trip, file);
    }

    private TripCoverPhotoResp toCoverPhoto(Trip trip, File file) {
        if (file != null && "UPLOADED".equals(file.getStatus())) {
            var ciUrls = fileUrlResolver.resolveUrls(file);
            if (StringUtils.hasText(ciUrls.url())) {
                return new TripCoverPhotoResp(
                        file.getId(),
                        ciUrls.url(),
                        ciUrls.thumbnailUrl(),
                        ciUrls.previewUrl(),
                        ciUrls.exportUrl(),
                        ciUrls.storyPortraitUrl(),
                        ciUrls.storyCardUrl(),
                        ciUrls.squareUrl(),
                        ciUrls.watermarkedUrl(),
                        file.getWidth(),
                        file.getHeight());
            }
        }
        return destinationCoverProvider.resolveTripCover(trip).orElse(null);
    }

    private TripStats defaultStats(Trip trip, String userId) {
        BigDecimal totalCny = expenseMapper.sumVisibleAmountCnyByTripIdAndUserId(trip.getId(), userId);
        long nodeCount = itineraryNodeMapper.countByTripId(trip.getId());
        if (nodeCount == 0) {
            nodeCount = itineraryNodeMapper.countByTripIdAndUserId(trip.getId(), trip.getUserId());
        }
        long journalCount = journalEntryMapper.countVisibleByTripIdAndUserId(trip.getId(), userId);
        return new TripStats(formatMoney(totalCny), nodeCount, journalCount);
    }

    private List<TripActivityLogResp.FieldChange> changes(TripActivityLogResp.FieldChange... changes) {
        return java.util.Arrays.stream(changes)
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    private TripActivityLogResp.FieldChange change(String field, Object oldValue, Object newValue) {
        String oldText = oldValue == null ? null : oldValue.toString();
        String newText = newValue == null ? null : newValue.toString();
        if (java.util.Objects.equals(oldText, newText)) {
            return null;
        }
        return new TripActivityLogResp.FieldChange(field, oldText, newText);
    }

    private TripListContext buildTripListContext(List<Trip> trips, String userId) {
        if (trips.isEmpty()) {
            return new TripListContext(Map.of(), Map.of());
        }
        List<String> tripIds = trips.stream().map(Trip::getId).toList();
        Map<String, BigDecimal> expenseByTripId = expenseMapper
                .sumVisibleAmountCnyByTripIdsAndUserId(tripIds, userId).stream()
                .collect(Collectors.toMap(
                        ExpenseMapper.TripAmountSummary::tripId,
                        summary -> summary.amountCny() == null ? BigDecimal.ZERO : summary.amountCny(),
                        (left, right) -> left));
        Map<String, Long> nodeCountByTripId = itineraryNodeMapper.countGroupByTripIds(tripIds).stream()
                .collect(Collectors.toMap(
                        ItineraryNodeMapper.TripCountSummary::tripId,
                        ItineraryNodeMapper.TripCountSummary::cnt,
                        (left, right) -> left));
        if (nodeCountByTripId.isEmpty()) {
            nodeCountByTripId = itineraryNodeMapper.countByTripIdsAndUserId(tripIds, userId).stream()
                    .collect(Collectors.toMap(
                            ItineraryNodeMapper.TripCountSummary::tripId,
                            ItineraryNodeMapper.TripCountSummary::cnt,
                            (left, right) -> left));
        }
        Map<String, Long> journalCountByTripId = journalEntryMapper
                .countVisibleGroupByTripIdsAndUserId(tripIds, userId).stream()
                .collect(Collectors.toMap(
                        JournalEntryMapper.TripCountSummary::tripId,
                        JournalEntryMapper.TripCountSummary::cnt,
                        (left, right) -> left));

        Map<String, BigDecimal> finalExpenseByTripId = expenseByTripId;
        Map<String, Long> finalNodeCountByTripId = nodeCountByTripId;
        Map<String, Long> finalJournalCountByTripId = journalCountByTripId;
        Map<String, TripStats> statsByTripId = tripIds.stream()
                .collect(Collectors.toMap(Function.identity(), tripId -> new TripStats(
                        formatMoney(finalExpenseByTripId.get(tripId)),
                        finalNodeCountByTripId.getOrDefault(tripId, 0L),
                        finalJournalCountByTripId.getOrDefault(tripId, 0L))));

        List<String> coverFileIds = trips.stream()
                .map(Trip::getCoverFileId)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        Map<String, File> filesById = coverFileIds.isEmpty()
                ? Map.of()
                : fileMapper.selectBatchIds(coverFileIds).stream()
                .collect(Collectors.toMap(File::getId, Function.identity(), (left, right) -> left));
        return new TripListContext(statsByTripId, filesById);
    }

    private TripStats zeroStats() {
        return new TripStats(ZERO_CNY, 0, 0);
    }

    private boolean isZero(BigDecimal amount) {
        return amount == null || amount.compareTo(BigDecimal.ZERO) == 0;
    }

    private String formatMoney(BigDecimal amount) {
        if (amount == null) {
            return ZERO_CNY;
        }
        return amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private record TripStats(String totalCny, long nodeCount, long journalCount) {
    }

    private record TripListContext(Map<String, TripStats> statsByTripId, Map<String, File> filesById) {
    }
}
