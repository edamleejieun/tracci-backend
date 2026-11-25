package com.tracci.module.place.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.tracci.common.enums.LocationProvider;
import com.tracci.common.exception.BizException;
import com.tracci.common.exception.NotFoundException;
import com.tracci.common.response.ErrorCode;
import com.tracci.common.response.PageResponse;
import com.tracci.common.util.DateTimeUtil;
import com.tracci.integration.amap.AmapPoiClient;
import com.tracci.integration.amap.dto.AmapPoiDTO;
import com.tracci.module.place.dto.request.PublishedPlaceListQuery;
import com.tracci.module.place.dto.response.PlaceDetailResponse;
import com.tracci.module.place.dto.response.PlaceListItemResponse;
import com.tracci.module.place.entity.Place;
import com.tracci.module.place.repository.PublishedPlaceRow;
import com.tracci.module.place.entity.PlaceProviderRef;
import com.tracci.module.place.enums.PlaceCategory;
import com.tracci.module.place.enums.PlaceCreatedSource;
import com.tracci.module.place.enums.PlaceScope;
import com.tracci.module.place.enums.PlaceStatus;
import com.tracci.module.place.repository.PlaceMapper;
import com.tracci.module.place.repository.PlaceProviderRefMapper;
import com.tracci.module.placecontent.dto.response.MediaAssetDto;
import com.tracci.module.placecontent.dto.response.PublishedContentDto;
import com.tracci.module.placecontent.service.PlaceContentService;
import com.tracci.module.savedplace.dto.request.LocationCandidate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PlaceServiceImpl implements PlaceService {

    static final int PUBLISHED_LIST_MAX_PAGE_SIZE = 50;

    private final PlaceMapper placeMapper;
    private final PlaceProviderRefMapper placeProviderRefMapper;
    private final AmapPoiClient amapPoiClient;
    private final PlaceContentService placeContentService;

    @Override
    @Transactional
    public Place resolveOrCreatePlace(LocationCandidate location, String placeScope, String category) {
        validateScopeAndCategory(placeScope, category);

        String provider = normalizeProvider(location.provider());
        String providerPlaceId = normalizeOptional(location.providerPlaceId());
        validateProviderIdentity(provider, providerPlaceId);
        boolean globallyShareableIdentity = isGloballyShareableIdentity(provider, providerPlaceId, placeScope);

        // 只有服务端可验证的高德 POI 身份才允许跨用户复用。
        if (globallyShareableIdentity) {
            PlaceProviderRef existingRef = findProviderRef(provider, providerPlaceId);
            if (existingRef != null) {
                return requireCompatibleProviderPlace(existingRef, placeScope);
            }
        }

        LocationCandidate canonicalLocation = location;
        String canonicalCategory = category;
        if (globallyShareableIdentity) {
            VerifiedAmapPlace verified = verifyAmapLocation(location);
            canonicalLocation = verified.location();
            canonicalCategory = verified.category().name();
        }

        Place place = new Place();
        place.setPlaceScope(placeScope);
        place.setCategory(canonicalCategory);
        place.setCanonicalName(requireName(canonicalLocation.name()));
        place.setAddress(normalizeOptional(canonicalLocation.address()));
        place.setCityName(normalizeOptional(canonicalLocation.cityName()));
        place.setCountry(normalizeOptional(canonicalLocation.country()));
        place.setLatitude(canonicalLocation.latitude());
        place.setLongitude(canonicalLocation.longitude());
        place.setDestinationKey(generateDestinationKey(canonicalLocation));
        place.setCreatedSource(resolveCreatedSource(provider));
        place.setStatus(PlaceStatus.ACTIVE.name());
        placeMapper.insert(place);

        if (globallyShareableIdentity) {
            PlaceProviderRef ref = new PlaceProviderRef();
            ref.setId(UUID.randomUUID().toString());
            ref.setPlaceId(place.getId());
            ref.setProvider(provider);
            ref.setProviderPlaceId(providerPlaceId);
            ref.setIsPrimary(true);
            ref.setLastCheckedAt(DateTimeUtil.nowUtc());
            ref.setCreatedAt(DateTimeUtil.nowUtc());
            ref.setUpdatedAt(ref.getCreatedAt());
            int inserted = placeProviderRefMapper.insertIfAbsent(ref);
            if (inserted == 0) {
                PlaceProviderRef existingRef = findProviderRef(provider, providerPlaceId);
                if (existingRef != null) {
                    Place existing = requireCompatibleProviderPlace(existingRef, placeScope);
                    placeMapper.deleteById(place.getId());
                    return existing;
                }
                throw new BizException(ErrorCode.PLACE_IDENTITY_UNAVAILABLE, "地点身份并发解析失败，请重试");
            }
        }

        return place;
    }

    @Override
    @Transactional(readOnly = true)
    public PlaceDetailResponse getPlaceDetail(String placeId) {
        // MERGED 时解析到 canonical ACTIVE Place；内容与媒体均按 canonical id 查询
        Place place = getPlaceById(placeId);
        String canonicalId = place.getId();

        PublishedContentDto published = placeContentService.findPublishedContent(canonicalId);
        List<MediaAssetDto> media = placeContentService.findApprovedMedia(canonicalId);

        return new PlaceDetailResponse(
                canonicalId,
                place.getCanonicalName(),
                place.getPlaceScope(),
                place.getCategory(),
                place.getAddress(),
                place.getCityName(),
                place.getCountry(),
                place.getLatitude(),
                place.getLongitude(),
                buildSupportedUsages(place.getPlaceScope()),
                null,
                published != null ? published.editorialContent() : null,
                published != null ? published.contentMeta() : null,
                media.isEmpty() ? null : media
        );
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<PlaceListItemResponse> listPublishedPlaces(PublishedPlaceListQuery query) {
        int page = query.page();
        int pageSize = query.pageSize();
        if (page < 1) {
            throw new BizException(ErrorCode.PARAM_INVALID, "page 从 1 开始");
        }
        if (pageSize < 1 || pageSize > PUBLISHED_LIST_MAX_PAGE_SIZE) {
            throw new BizException(ErrorCode.PARAM_INVALID, "pageSize 范围为 1–50");
        }

        String placeScope = parseOptionalPlaceScope(query.placeScope());
        String category = parseOptionalCategory(query.category());
        String cityName = normalizeOptional(query.cityName());

        long total = placeMapper.countPublishedPlaces(placeScope, cityName, category);
        if (total == 0) {
            return PageResponse.of(List.of(), 0, page, pageSize);
        }

        List<PublishedPlaceRow> rows = placeMapper.selectPublishedPlaces(
                placeScope, cityName, category, pageSize, (long) (page - 1) * pageSize);
        if (rows == null || rows.isEmpty()) {
            return PageResponse.of(List.of(), total, page, pageSize);
        }

        List<String> placeIds = rows.stream().map(PublishedPlaceRow::getId).toList();
        Map<String, MediaAssetDto> covers = placeContentService.findCoverMediaByPlaceIds(placeIds);

        List<PlaceListItemResponse> items = new ArrayList<>(rows.size());
        for (PublishedPlaceRow row : rows) {
            items.add(PlaceListAssembler.toItem(row, covers.get(row.getId())));
        }
        return PageResponse.of(items, total, page, pageSize);
    }

    @Override
    public Place getPlaceById(String placeId) {
        Place place = loadActivePlace(placeId);
        if (place == null) {
            throw new NotFoundException(ErrorCode.PLACE_NOT_FOUND);
        }
        return place;
    }

    @Override
    public void validateScopeAndCategory(String placeScope, String category) {
        PlaceScope scope;
        PlaceCategory cat;
        try {
            scope = PlaceScope.valueOf(placeScope);
        } catch (IllegalArgumentException e) {
            throw new BizException(ErrorCode.PLACE_INVALID_SCOPE_CATEGORY, "placeScope 仅支持 DESTINATION / POI");
        }
        try {
            cat = PlaceCategory.valueOf(category);
        } catch (IllegalArgumentException e) {
            throw new BizException(ErrorCode.PLACE_INVALID_SCOPE_CATEGORY, "category 不合法");
        }

        if (scope == PlaceScope.DESTINATION && cat != PlaceCategory.CITY && cat != PlaceCategory.REGION) {
            throw new BizException(ErrorCode.PLACE_INVALID_SCOPE_CATEGORY,
                    "DESTINATION 只允许 CITY / REGION");
        }
        if (scope == PlaceScope.POI && (cat == PlaceCategory.CITY || cat == PlaceCategory.REGION)) {
            throw new BizException(ErrorCode.PLACE_INVALID_SCOPE_CATEGORY,
                    "POI 不允许 CITY / REGION");
        }
    }

    // ---- private helpers ----

    /**
     * 加载可用地点：ACTIVE 直接返回；MERGED 跟随 {@code merged_into_id} 到 canonical ACTIVE。
     */
    private Place loadActivePlace(String placeId) {
        if (!StringUtils.hasText(placeId)) {
            return null;
        }
        Place place = placeMapper.selectById(placeId);
        if (place == null || place.getDeletedAt() != null) {
            return null;
        }
        if (PlaceStatus.ACTIVE.name().equals(place.getStatus())) {
            return place;
        }
        if (PlaceStatus.MERGED.name().equals(place.getStatus())
                && StringUtils.hasText(place.getMergedIntoId())) {
            Place target = placeMapper.selectById(place.getMergedIntoId());
            if (target != null
                    && target.getDeletedAt() == null
                    && PlaceStatus.ACTIVE.name().equals(target.getStatus())) {
                return target;
            }
        }
        return null;
    }

    private Place requireCompatibleProviderPlace(
            PlaceProviderRef providerRef, String expectedScope) {
        Place place = loadProviderPlace(providerRef.getPlaceId());
        if (!expectedScope.equals(place.getPlaceScope())) {
            throw new BizException(ErrorCode.PLACE_IDENTITY_UNAVAILABLE,
                    "供应商地点身份与已有地点范围不一致");
        }
        return place;
    }

    private Place loadProviderPlace(String placeId) {
        Place place = placeMapper.selectById(placeId);
        if (place == null || place.getDeletedAt() != null) {
            throw new BizException(ErrorCode.PLACE_IDENTITY_UNAVAILABLE, "供应商地点身份已失效");
        }
        if (PlaceStatus.ACTIVE.name().equals(place.getStatus())) {
            return place;
        }
        if (PlaceStatus.MERGED.name().equals(place.getStatus())
                && StringUtils.hasText(place.getMergedIntoId())) {
            Place mergedInto = loadActivePlace(place.getMergedIntoId());
            if (mergedInto != null) {
                return mergedInto;
            }
        }
        throw new BizException(ErrorCode.PLACE_IDENTITY_UNAVAILABLE, "供应商地点身份已失效");
    }

    private PlaceProviderRef findProviderRef(String provider, String providerPlaceId) {
        return placeProviderRefMapper.selectOne(new QueryWrapper<PlaceProviderRef>()
                .eq("provider", provider.toUpperCase(Locale.ROOT))
                .eq("provider_place_id", providerPlaceId)
                .isNull("deleted_at")
                .last("LIMIT 1"));
    }

    private String normalizeProvider(String provider) {
        if (!StringUtils.hasText(provider)) {
            return LocationProvider.MANUAL.name();
        }
        try {
            return LocationProvider.valueOf(provider.trim().toUpperCase(Locale.ROOT)).name();
        } catch (IllegalArgumentException e) {
            throw new BizException(ErrorCode.PLACE_IDENTITY_UNAVAILABLE, "provider 不支持");
        }
    }

    private void validateProviderIdentity(String provider, String providerPlaceId) {
        if (LocationProvider.MANUAL.name().equals(provider) && StringUtils.hasText(providerPlaceId)) {
            throw new BizException(ErrorCode.PLACE_IDENTITY_UNAVAILABLE,
                    "手工地点不能携带供应商地点 ID");
        }
    }

    private boolean isGloballyShareableIdentity(
            String provider, String providerPlaceId, String placeScope) {
        return LocationProvider.AMAP.name().equals(provider)
                && StringUtils.hasText(providerPlaceId)
                && PlaceScope.POI.name().equals(placeScope);
    }

    private VerifiedAmapPlace verifyAmapLocation(LocationCandidate request) {
        AmapPoiDTO poi = amapPoiClient.getDetail(request.providerPlaceId());
        if (poi == null || !request.providerPlaceId().equals(normalizeOptional(poi.id()))
                || !StringUtils.hasText(normalizeOptional(poi.name()))) {
            throw new BizException(ErrorCode.PLACE_IDENTITY_UNAVAILABLE, "高德地点身份无法验证");
        }
        PlaceCategory verifiedCategory = inferAmapCategory(poi.typecode());
        Coordinate coordinate = parseCoordinate(poi.location());
        LocationCandidate canonicalLocation = new LocationCandidate(
                normalizeOptional(poi.name()),
                firstText(poi.address()),
                LocationProvider.AMAP.name(),
                normalizeOptional(poi.id()),
                "中国",
                firstText(poi.cityname()),
                coordinate.latitude(),
                coordinate.longitude(),
                normalizeOptional(poi.adcode()),
                normalizeOptional(poi.citycode())
        );
        return new VerifiedAmapPlace(canonicalLocation, verifiedCategory);
    }

    private PlaceCategory inferAmapCategory(String typecode) {
        String normalized = normalizeOptional(typecode);
        if (!StringUtils.hasText(normalized)) {
            return PlaceCategory.OTHER;
        }
        if (normalized.startsWith("05")) {
            return PlaceCategory.RESTAURANT;
        }
        if (normalized.startsWith("06")) {
            return PlaceCategory.SHOPPING;
        }
        if (normalized.startsWith("10")) {
            return PlaceCategory.HOTEL;
        }
        if (normalized.startsWith("11")) {
            return PlaceCategory.ATTRACTION;
        }
        if (normalized.startsWith("15")) {
            return PlaceCategory.TRANSPORT;
        }
        return PlaceCategory.OTHER;
    }

    private Coordinate parseCoordinate(String location) {
        if (!StringUtils.hasText(location)) {
            return new Coordinate(null, null);
        }
        String[] parts = location.split(",");
        if (parts.length != 2) {
            return new Coordinate(null, null);
        }
        try {
            return new Coordinate(new BigDecimal(parts[1].trim()), new BigDecimal(parts[0].trim()));
        } catch (NumberFormatException e) {
            return new Coordinate(null, null);
        }
    }

    private String firstText(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        if (node.isArray()) {
            for (JsonNode item : node) {
                String text = firstText(item);
                if (StringUtils.hasText(text)) {
                    return text;
                }
            }
            return null;
        }
        if (node.isObject()) {
            return null;
        }
        return normalizeOptional(node.asText(null));
    }

    private List<String> buildSupportedUsages(String placeScope) {
        return PlaceListAssembler.supportedUsages(placeScope);
    }

    private String parseOptionalPlaceScope(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            return PlaceScope.valueOf(raw.trim().toUpperCase(Locale.ROOT)).name();
        } catch (IllegalArgumentException e) {
            throw new BizException(ErrorCode.PLACE_INVALID_SCOPE_CATEGORY, "placeScope 仅支持 DESTINATION / POI");
        }
    }

    private String parseOptionalCategory(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            return PlaceCategory.valueOf(raw.trim().toUpperCase(Locale.ROOT)).name();
        } catch (IllegalArgumentException e) {
            throw new BizException(ErrorCode.PLACE_INVALID_SCOPE_CATEGORY, "category 不合法");
        }
    }

    /**
     * 生成 destinationKey（DESTINATION 与 POI 共用，用于目的地匹配）。
     * 优先级：adcode → cityCode → country/cityName → cityName。
     */
    private String generateDestinationKey(LocationCandidate location) {
        String adcode = normalizeOptional(location.adcode());
        if (StringUtils.hasText(adcode)) {
            return "adcode:" + adcode;
        }
        String cityCode = normalizeOptional(location.cityCode());
        if (StringUtils.hasText(cityCode)) {
            return "cityCode:" + cityCode;
        }
        String country = normalizeOptional(location.country());
        String cityName = normalizeOptional(location.cityName());
        if (StringUtils.hasText(country) && StringUtils.hasText(cityName)) {
            return "loc:" + country.toLowerCase(Locale.ROOT) + "/" + cityName.toLowerCase(Locale.ROOT);
        }
        if (StringUtils.hasText(cityName)) {
            return "city:" + cityName.toLowerCase(Locale.ROOT);
        }
        return null;
    }

    private String resolveCreatedSource(String provider) {
        return PlaceCreatedSource.valueOf(provider).name();
    }

    private String requireName(String name) {
        String normalized = normalizeOptional(name);
        if (!StringUtils.hasText(normalized)) {
            throw new BizException(ErrorCode.PLACE_IDENTITY_UNAVAILABLE, "地点名称不能为空");
        }
        return normalized;
    }

    private String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return StringUtils.hasText(trimmed) ? trimmed : null;
    }

    private record Coordinate(BigDecimal latitude, BigDecimal longitude) {
    }

    private record VerifiedAmapPlace(LocationCandidate location, PlaceCategory category) {
    }
}
