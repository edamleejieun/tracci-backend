package com.tracci.module.savedplace.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tracci.common.exception.BizException;
import com.tracci.common.exception.NotFoundException;
import com.tracci.common.response.ErrorCode;
import com.tracci.common.response.PageResponse;
import com.tracci.common.util.DateTimeUtil;
import com.tracci.integration.wechat.WechatContentSecurityClient;
import com.tracci.module.auth.entity.UserAuthIdentity;
import com.tracci.module.auth.enums.AuthProvider;
import com.tracci.module.auth.repository.UserAuthIdentityMapper;
import com.tracci.module.place.dto.response.PlaceUsageSummary;
import com.tracci.module.place.dto.response.PlaceUserContext;
import com.tracci.module.place.entity.Place;
import com.tracci.module.place.enums.PlaceScope;
import com.tracci.module.place.enums.PlaceStatus;
import com.tracci.module.place.service.PlaceService;
import com.tracci.module.savedplace.dto.request.CreateSavedPlaceRequest;
import com.tracci.module.savedplace.dto.request.LocationCandidate;
import com.tracci.module.savedplace.dto.request.UpdateSavedPlaceRequest;
import com.tracci.module.savedplace.dto.response.PlaceBrief;
import com.tracci.module.savedplace.dto.response.SavedPlaceSummary;
import com.tracci.module.savedplace.entity.SavedPlace;
import com.tracci.module.savedplace.entity.SavedPlaceUsage;
import com.tracci.module.savedplace.enums.SavedPlaceSource;
import com.tracci.module.savedplace.enums.UsageType;
import com.tracci.module.savedplace.repository.SavedPlaceMapper;
import com.tracci.module.savedplace.repository.SavedPlaceUsageMapper;
import com.tracci.module.trip.entity.Trip;
import com.tracci.module.trip.enums.TripPermission;
import com.tracci.module.trip.service.TripAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SavedPlaceServiceImpl implements SavedPlaceService {

    private static final int MAX_PAGE_SIZE = 100;

    private final SavedPlaceMapper savedPlaceMapper;
    private final SavedPlaceUsageMapper savedPlaceUsageMapper;
    private final PlaceService placeService;
    private final TripAccessService tripAccessService;
    private final UserAuthIdentityMapper authIdentityMapper;
    private final WechatContentSecurityClient wechatContentSecurityClient;

    @Override
    @Transactional
    public SavedPlaceSummary createSavedPlace(String userId, CreateSavedPlaceRequest request) {
        ResolvedSavedPlace resolved = resolvePlaceForSave(request);
        Place place = resolved.place();
        checkContentSafety(userId, request.location() != null ? request.location().name() : null, request.note());

        SavedPlace existing = findSavedPlace(userId, place.getId());
        if (existing != null) {
            throw new BizException(ErrorCode.SAVED_PLACE_ALREADY_EXISTS);
        }

        SavedPlace savedPlace = new SavedPlace();
        savedPlace.setUserId(userId);
        savedPlace.setPlaceId(place.getId());
        savedPlace.setNote(normalizeOptional(request.note()));
        savedPlace.setSource(resolved.source());
        try {
            savedPlaceMapper.insert(savedPlace);
        } catch (DuplicateKeyException e) {
            throw new BizException(ErrorCode.SAVED_PLACE_ALREADY_EXISTS);
        }

        return toSummary(savedPlace, place);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<SavedPlaceSummary> listSavedPlaces(
            String userId, String keyword, String placeScope, String category,
            String usage, String tripId, int page, int pageSize) {

        int safePage = Math.max(page, 1);
        int safePageSize = Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE);

        QueryWrapper<SavedPlace> qw = new QueryWrapper<SavedPlace>()
                .eq("user_id", userId)
                .isNull("deleted_at")
                .orderByDesc("created_at");

        String effectiveScope = normalizeOptional(placeScope);
        UsageType usageType = null;
        if (StringUtils.hasText(usage)) {
            try {
                usageType = UsageType.valueOf(usage.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new BizException(ErrorCode.PARAM_INVALID, "usage 仅支持 TRIP_DESTINATION / ITINERARY_NODE");
            }
            if (usageType == UsageType.TRIP_DESTINATION) {
                effectiveScope = PlaceScope.DESTINATION.name();
            } else if (usageType == UsageType.ITINERARY_NODE) {
                effectiveScope = PlaceScope.POI.name();
            }
        }

        List<String> tripDestKeys = List.of();
        String tripDestinationPlaceId = null;
        if (usageType == UsageType.ITINERARY_NODE && !StringUtils.hasText(tripId)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "usage=ITINERARY_NODE 时 tripId 不能为空");
        }
        if (StringUtils.hasText(tripId) && PlaceScope.POI.name().equals(effectiveScope)) {
            TripAccessService.TripAccess access = tripAccessService.requirePermission(
                    tripId, userId, TripPermission.READ);
            tripDestKeys = buildTripDestinationKeys(access.trip());
            tripDestinationPlaceId = access.trip().getDestinationPlaceId();
            if (tripDestKeys.isEmpty() && !StringUtils.hasText(tripDestinationPlaceId)) {
                qw.apply("1 = 0");
            }
        }

        applyPlaceExistsFilter(qw, keyword, effectiveScope, category, tripDestKeys, tripDestinationPlaceId);

        Page<SavedPlace> pageObj = new Page<>(safePage, safePageSize);
        Page<SavedPlace> result = savedPlaceMapper.selectPage(pageObj, qw);
        List<SavedPlace> records = result.getRecords();

        List<SavedPlaceSummary> summaries = records.stream()
                .map(sp -> toSummary(sp, placeService.getPlaceById(sp.getPlaceId())))
                .toList();

        return PageResponse.of(summaries, result.getTotal(), safePage, safePageSize);
    }

    @Override
    @Transactional
    public SavedPlaceSummary updateSavedPlace(String userId, String savedPlaceId, UpdateSavedPlaceRequest request) {
        SavedPlace savedPlace = requireOwnedSavedPlace(userId, savedPlaceId);
        if (request.note() != null) {
            String note = normalizeOptional(request.note());
            checkContentSafety(userId, null, note);
            savedPlace.setNote(note);
        }
        savedPlaceMapper.updateById(savedPlace);
        Place place = placeService.getPlaceById(savedPlace.getPlaceId());
        return toSummary(savedPlace, place);
    }

    @Override
    @Transactional
    public void deleteSavedPlace(String userId, String savedPlaceId) {
        SavedPlace savedPlace = requireOwnedSavedPlace(userId, savedPlaceId);
        savedPlaceMapper.deleteById(savedPlace.getId());
    }

    @Override
    @Transactional(readOnly = true)
    public Place resolveForTripDestination(String userId, String savedPlaceId) {
        SavedPlace savedPlace = requireOwnedSavedPlace(userId, savedPlaceId);
        Place place = placeService.getPlaceById(savedPlace.getPlaceId());
        if (!PlaceScope.DESTINATION.name().equals(place.getPlaceScope())) {
            throw new BizException(ErrorCode.SAVED_PLACE_SCOPE_MISMATCH,
                    "该地点不是城市/地区，不能作为旅程目的地");
        }
        return place;
    }

    @Override
    @Transactional(readOnly = true)
    public Place resolveForItineraryNode(String userId, String savedPlaceId, String tripId) {
        SavedPlace savedPlace = requireOwnedSavedPlace(userId, savedPlaceId);
        Place place = placeService.getPlaceById(savedPlace.getPlaceId());
        if (!PlaceScope.POI.name().equals(place.getPlaceScope())) {
            throw new BizException(ErrorCode.SAVED_PLACE_SCOPE_MISMATCH,
                    "该地点不是具体地点，不能作为行程节点");
        }
        // 校验旅程可读且目的地匹配（内部会 requirePermission）
        TripAccessService.TripAccess access = tripAccessService.requirePermission(
                tripId, userId, TripPermission.READ);
        validateDestinationMatch(place, access.trip());
        return place;
    }

    @Override
    @Transactional
    public void recordTripUsage(String userId, String savedPlaceId, String tripId) {
        SavedPlace savedPlace = requireOwnedSavedPlace(userId, savedPlaceId);
        SavedPlaceUsage usage = new SavedPlaceUsage();
        usage.setUserId(userId);
        usage.setSavedPlaceId(savedPlace.getId());
        usage.setTripId(tripId);
        usage.setUsageType(UsageType.TRIP_DESTINATION.name());
        savedPlaceUsageMapper.insert(usage);
    }

    @Override
    @Transactional
    public void recordNodeUsage(String userId, String savedPlaceId, String tripId, String nodeId) {
        SavedPlace savedPlace = requireOwnedSavedPlace(userId, savedPlaceId);
        SavedPlaceUsage usage = new SavedPlaceUsage();
        usage.setUserId(userId);
        usage.setSavedPlaceId(savedPlace.getId());
        usage.setTripId(tripId);
        usage.setItineraryNodeId(nodeId);
        usage.setUsageType(UsageType.ITINERARY_NODE.name());
        usage.setId(UUID.randomUUID().toString());
        usage.setCreatedAt(DateTimeUtil.nowUtc());
        usage.setUpdatedAt(usage.getCreatedAt());
        savedPlaceUsageMapper.insertNodeUsageIfAbsent(usage);
    }

    @Override
    @Transactional(readOnly = true)
    public PlaceUserContext buildUserContext(String userId, String placeId) {
        if (!StringUtils.hasText(userId) || !StringUtils.hasText(placeId)) {
            return new PlaceUserContext(false, null, null, null);
        }
        SavedPlace saved = findSavedPlace(userId, placeId);
        if (saved == null) {
            return new PlaceUserContext(false, null, null, null);
        }
        return new PlaceUserContext(true, saved.getId(), saved.getNote(), buildUsageSummary(saved.getId()));
    }

    @Override
    @Transactional(readOnly = true)
    public Set<String> findSavedPlaceIds(String userId, Collection<String> placeIds) {
        if (!StringUtils.hasText(userId) || placeIds == null || placeIds.isEmpty()) {
            return Set.of();
        }
        List<SavedPlace> rows = savedPlaceMapper.selectList(new QueryWrapper<SavedPlace>()
                .eq("user_id", userId)
                .in("place_id", placeIds)
                .isNull("deleted_at"));
        if (rows == null || rows.isEmpty()) {
            return Set.of();
        }
        return rows.stream().map(SavedPlace::getPlaceId).collect(Collectors.toSet());
    }

    // ---- private helpers ----

    private ResolvedSavedPlace resolvePlaceForSave(CreateSavedPlaceRequest request) {
        String placeId = normalizeOptional(request.placeId());
        boolean hasPlaceId = StringUtils.hasText(placeId);
        boolean hasLocation = request.location() != null;
        if (hasPlaceId == hasLocation) {
            throw new BizException(ErrorCode.PLACE_INVALID_SCOPE_CATEGORY,
                    "placeId 与 location 必须且只能提供一个");
        }

        if (hasPlaceId) {
            Place place = placeService.getPlaceById(placeId);
            validateOptionalPlaceClassification(request, place);
            return new ResolvedSavedPlace(place, SavedPlaceSource.MANUAL.name());
        }

        if (!StringUtils.hasText(request.placeScope()) || !StringUtils.hasText(request.category())) {
            throw new BizException(ErrorCode.PLACE_INVALID_SCOPE_CATEGORY,
                    "使用 location 收藏时 placeScope 和 category 不能为空");
        }
        placeService.validateScopeAndCategory(request.placeScope(), request.category());
        Place place = placeService.resolveOrCreatePlace(
                request.location(), request.placeScope(), request.category());
        return new ResolvedSavedPlace(place, resolveSource(request.location()));
    }

    private void validateOptionalPlaceClassification(CreateSavedPlaceRequest request, Place place) {
        boolean hasScope = StringUtils.hasText(request.placeScope());
        boolean hasCategory = StringUtils.hasText(request.category());
        if (!hasScope && !hasCategory) {
            return;
        }
        if (!hasScope || !hasCategory) {
            throw new BizException(ErrorCode.PLACE_INVALID_SCOPE_CATEGORY,
                    "placeScope 和 category 必须同时提供");
        }
        placeService.validateScopeAndCategory(request.placeScope(), request.category());
        if (!request.placeScope().equals(place.getPlaceScope())
                || !request.category().equals(place.getCategory())) {
            throw new BizException(ErrorCode.PLACE_INVALID_SCOPE_CATEGORY,
                    "请求分类与已有地点不一致");
        }
    }

    private SavedPlace requireOwnedSavedPlace(String userId, String savedPlaceId) {
        SavedPlace sp = savedPlaceMapper.selectById(savedPlaceId);
        if (sp == null || sp.getDeletedAt() != null) {
            throw new NotFoundException(ErrorCode.SAVED_PLACE_NOT_FOUND);
        }
        if (!userId.equals(sp.getUserId())) {
            throw new NotFoundException(ErrorCode.SAVED_PLACE_NOT_FOUND);
        }
        return sp;
    }

    private SavedPlace findSavedPlace(String userId, String placeId) {
        return savedPlaceMapper.selectOne(new QueryWrapper<SavedPlace>()
                .eq("user_id", userId)
                .eq("place_id", placeId)
                .isNull("deleted_at")
                .last("LIMIT 1"));
    }

    /**
     * 用 EXISTS 子查询过滤 place 维度条件，避免先拉全表 place id。
     */
    private void applyPlaceExistsFilter(
            QueryWrapper<SavedPlace> qw,
            String keyword,
            String placeScope,
            String category,
            List<String> tripDestKeys,
            String tripDestinationPlaceId) {

        boolean hasKeyword = StringUtils.hasText(keyword);
        boolean hasScope = StringUtils.hasText(placeScope);
        boolean hasCategory = StringUtils.hasText(category);
        boolean hasTripMatch = (tripDestKeys != null && !tripDestKeys.isEmpty())
                || StringUtils.hasText(tripDestinationPlaceId);

        if (!hasKeyword && !hasScope && !hasCategory && !hasTripMatch) {
            return;
        }

        if (hasScope) {
            try {
                PlaceScope.valueOf(placeScope);
            } catch (IllegalArgumentException e) {
                throw new BizException(ErrorCode.PLACE_INVALID_SCOPE_CATEGORY, "placeScope 仅支持 DESTINATION / POI");
            }
        }
        if (hasCategory) {
            try {
                com.tracci.module.place.enums.PlaceCategory.valueOf(category);
            } catch (IllegalArgumentException e) {
                throw new BizException(ErrorCode.PLACE_INVALID_SCOPE_CATEGORY, "category 不合法");
            }
        }

        StringBuilder sql = new StringBuilder(
                "EXISTS (SELECT 1 FROM places p WHERE p.id = place_id AND p.deleted_at IS NULL AND p.status = {0}");
        List<Object> params = new ArrayList<>();
        params.add(PlaceStatus.ACTIVE.name());
        int idx = 1;

        if (hasScope) {
            sql.append(" AND p.place_scope = {").append(idx++).append('}');
            params.add(placeScope);
        }
        if (hasCategory) {
            sql.append(" AND p.category = {").append(idx++).append('}');
            params.add(category);
        }
        if (hasKeyword) {
            // 地点名/地址/城市 + 收藏私有备注（外层 saved_places.note）
            String like = "%" + escapeLike(keyword.trim()) + "%";
            sql.append(" AND (p.canonical_name LIKE {").append(idx)
                    .append("} OR p.address LIKE {").append(idx)
                    .append("} OR p.city_name LIKE {").append(idx)
                    .append("} OR note LIKE {").append(idx).append("})");
            params.add(like);
            idx++;
        }
        if (hasTripMatch) {
            // 仅推荐能够明确匹配旅程目的地的地点；信息不足不参与自动推荐。
            sql.append(" AND (1 = 0");
            if (tripDestKeys != null) {
                for (String key : tripDestKeys) {
                    sql.append(" OR p.destination_key = {").append(idx++).append('}');
                    params.add(key);
                }
            }
            if (StringUtils.hasText(tripDestinationPlaceId)) {
                sql.append(" OR p.parent_place_id = {").append(idx++).append('}');
                params.add(tripDestinationPlaceId);
            }
            sql.append(')');
        }
        sql.append(')');
        qw.apply(sql.toString(), params.toArray());
    }

    private void validateDestinationMatch(Place place, Trip trip) {
        // parent_place_id 精确挂靠优先
        if (StringUtils.hasText(place.getParentPlaceId())
                && StringUtils.hasText(trip.getDestinationPlaceId())
                && place.getParentPlaceId().equals(trip.getDestinationPlaceId())) {
            return;
        }
        List<String> tripKeys = buildTripDestinationKeys(trip);
        String placeDestKey = place.getDestinationKey();
        // 信息不足不能自动加入当前旅程。
        if (!StringUtils.hasText(placeDestKey) || tripKeys.isEmpty()) {
            throw new BizException(ErrorCode.SAVED_PLACE_DESTINATION_MISMATCH,
                    "地点或旅程缺少可匹配的目的地信息");
        }
        if (!tripKeys.contains(placeDestKey)) {
            throw new BizException(ErrorCode.SAVED_PLACE_DESTINATION_MISMATCH);
        }
    }

    private List<String> buildTripDestinationKeys(Trip trip) {
        List<String> keys = new ArrayList<>();
        if (StringUtils.hasText(trip.getDestinationAdcode())) {
            keys.add("adcode:" + trip.getDestinationAdcode().trim());
        }
        if (StringUtils.hasText(trip.getDestinationCityCode())) {
            keys.add("cityCode:" + trip.getDestinationCityCode().trim());
        }
        if (StringUtils.hasText(trip.getDestinationCountry()) && StringUtils.hasText(trip.getDestinationCityName())) {
            keys.add("loc:" + trip.getDestinationCountry().trim().toLowerCase(Locale.ROOT)
                    + "/" + trip.getDestinationCityName().trim().toLowerCase(Locale.ROOT));
        } else if (StringUtils.hasText(trip.getDestinationCityName())) {
            keys.add("city:" + trip.getDestinationCityName().trim().toLowerCase(Locale.ROOT));
        }
        // 从 destination_place 补 key（收藏建旅后结构化字段可能仅存在于 Place）
        if (StringUtils.hasText(trip.getDestinationPlaceId())) {
            try {
                Place destPlace = placeService.getPlaceById(trip.getDestinationPlaceId());
                if (StringUtils.hasText(destPlace.getDestinationKey())
                        && !keys.contains(destPlace.getDestinationKey())) {
                    keys.add(destPlace.getDestinationKey());
                }
            } catch (NotFoundException ignored) {
                // 目的地 Place 已不可用时仅用 trip 快照字段
            }
        }
        return keys;
    }

    private void checkContentSafety(String userId, String name, String note) {
        String openid = findWechatMiniOpenid(userId);
        if (!StringUtils.hasText(openid)) {
            return;
        }
        StringBuilder builder = new StringBuilder();
        appendContent(builder, name);
        appendContent(builder, note);
        String content = builder.toString().trim();
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

    private void appendContent(StringBuilder builder, String value) {
        String normalized = normalizeOptional(value);
        if (normalized == null) {
            return;
        }
        if (!builder.isEmpty()) {
            builder.append(' ');
        }
        builder.append(normalized);
    }

    private String resolveSource(LocationCandidate location) {
        String provider = location != null ? normalizeOptional(location.provider()) : null;
        if (StringUtils.hasText(provider) && !"MANUAL".equalsIgnoreCase(provider)) {
            return SavedPlaceSource.POI_SEARCH.name();
        }
        return SavedPlaceSource.MANUAL.name();
    }

    private record ResolvedSavedPlace(Place place, String source) {
    }

    private SavedPlaceSummary toSummary(SavedPlace sp, Place place) {
        PlaceBrief brief = new PlaceBrief(
                place.getCanonicalName(),
                place.getPlaceScope(),
                place.getCategory(),
                place.getAddress(),
                place.getCityName(),
                place.getCountry(),
                place.getLatitude(),
                place.getLongitude(),
                buildSupportedUsages(place.getPlaceScope())
        );
        return new SavedPlaceSummary(
                sp.getId(),
                sp.getPlaceId(),
                brief,
                sp.getNote(),
                buildUsageSummary(sp.getId()),
                DateTimeUtil.toInstantString(sp.getCreatedAt()),
                DateTimeUtil.toInstantString(sp.getUpdatedAt())
        );
    }

    private PlaceUsageSummary buildUsageSummary(String savedPlaceId) {
        List<SavedPlaceUsage> usages = savedPlaceUsageMapper.selectList(
                new QueryWrapper<SavedPlaceUsage>()
                        .eq("saved_place_id", savedPlaceId)
                        .isNull("deleted_at")
                        .orderByDesc("created_at"));
        long tripCount = usages.stream()
                .map(SavedPlaceUsage::getTripId)
                .distinct()
                .count();
        String lastUsedTripId = usages.isEmpty() ? null : usages.getFirst().getTripId();
        return new PlaceUsageSummary(tripCount, lastUsedTripId);
    }

    private List<String> buildSupportedUsages(String placeScope) {
        List<String> usages = new ArrayList<>();
        if (PlaceScope.DESTINATION.name().equals(placeScope)) {
            usages.add(UsageType.TRIP_DESTINATION.name());
        } else if (PlaceScope.POI.name().equals(placeScope)) {
            usages.add(UsageType.ITINERARY_NODE.name());
        }
        return usages;
    }

    private String escapeLike(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }

    private String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return StringUtils.hasText(trimmed) ? trimmed : null;
    }
}
