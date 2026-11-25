package com.tracci.module.placecontent.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.tracci.common.exception.BizException;
import com.tracci.common.exception.NotFoundException;
import com.tracci.common.response.ErrorCode;
import com.tracci.common.response.PageResponse;
import com.tracci.common.util.DateTimeUtil;
import com.tracci.module.location.dto.request.LocationPoiSearchReq;
import com.tracci.module.location.dto.response.LocationPoiDTO;
import com.tracci.module.location.service.LocationPoiService;
import com.tracci.module.place.entity.Place;
import com.tracci.module.place.enums.PlaceCategory;
import com.tracci.module.place.enums.PlaceScope;
import com.tracci.module.place.enums.PlaceStatus;
import com.tracci.module.place.repository.PlaceMapper;
import com.tracci.module.place.service.PlaceService;
import com.tracci.module.placecontent.dto.request.PlaceAdminCreateRequest;
import com.tracci.module.placecontent.dto.request.PlaceAdminSearchQuery;
import com.tracci.module.placecontent.dto.request.PlaceAdminUpdateRequest;
import com.tracci.module.placecontent.dto.request.PlaceResolveFromPoiRequest;
import com.tracci.module.placecontent.dto.response.PlaceAdminSearchItem;
import com.tracci.module.savedplace.dto.request.LocationCandidate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 管理端地点解析实现。
 * 依赖方向单向：placecontent(resolve) → place / location / placecontent(search)，
 * 不反向注入，保持 Spring 装配无环。
 */
@Service
@RequiredArgsConstructor
public class PlaceAdminResolveServiceImpl implements PlaceAdminResolveService {

    private final PlaceService placeService;
    private final LocationPoiService locationPoiService;
    private final PlaceContentService placeContentService;
    private final PlaceMapper placeMapper;

    @Override
    public List<LocationPoiDTO> listPoiSuggestions(String keyword, String city) {
        if (!StringUtils.hasText(keyword) || keyword.trim().length() < 2) {
            throw new BizException(ErrorCode.PARAM_INVALID, "keyword 至少需要 2 个字符");
        }
        LocationPoiSearchReq req = new LocationPoiSearchReq(
                keyword.trim(), StringUtils.hasText(city) ? city.trim() : null,
                null, null, 1, 10, "ADMIN");
        return locationPoiService.search(req).items();
    }

    @Override
    @Transactional
    public PlaceAdminSearchItem resolvePlaceFromPoi(PlaceResolveFromPoiRequest request) {
        Place destination = requireDestinationIfPresent(request.destinationPlaceId());
        String cityName = destination != null
                ? destination.getCanonicalName()
                : normalizeOptional(request.cityName());
        String country = destination != null
                ? firstText(destination.getCountry(), null)
                : null;

        LocationCandidate candidate = new LocationCandidate(
                request.name(),
                request.address(),
                "AMAP",
                request.poiId(),
                country,
                cityName,
                request.latitude(),
                request.longitude(),
                request.adcode(),
                request.cityCode());
        Place place = placeService.resolveOrCreatePlace(
                candidate, PlaceScope.POI.name(), PlaceCategory.ATTRACTION.name());
        if (destination != null) {
            stampDestination(place, destination);
        }
        return toItem(place.getId());
    }

    @Override
    @Transactional
    public PlaceAdminSearchItem createPlace(PlaceAdminCreateRequest request) {
        String scope = request.placeScope().trim().toUpperCase();
        String category = request.category().trim().toUpperCase();
        placeService.validateScopeAndCategory(scope, category);
        String name = requireText(request.name(), "地点名称不能为空");

        if (PlaceScope.DESTINATION.name().equals(scope)) {
            String country = requireText(request.country(), "城市需要填写国家，例如 中国 / 日本");
            Place existing = findActiveDestinationByName(name, country);
            if (existing != null) {
                return toItem(existing.getId());
            }
            LocationCandidate candidate = new LocationCandidate(
                    name,
                    request.address(),
                    "MANUAL",
                    null,
                    country,
                    name,
                    request.latitude(),
                    request.longitude(),
                    null,
                    null);
            Place created = placeService.resolveOrCreatePlace(candidate, scope, category);
            return toItem(created.getId());
        }

        Place destination = requireDestination(request.destinationPlaceId());
        LocationCandidate candidate = new LocationCandidate(
                name,
                request.address(),
                "MANUAL",
                null,
                firstText(request.country(), destination.getCountry()),
                destination.getCanonicalName(),
                request.latitude(),
                request.longitude(),
                null,
                null);
        Place created = placeService.resolveOrCreatePlace(candidate, scope, category);
        stampDestination(created, destination);
        return toItem(created.getId());
    }

    @Override
    @Transactional
    public PlaceAdminSearchItem updatePlace(String placeId, PlaceAdminUpdateRequest request) {
        Place place = requireActivePlace(placeId);
        String oldName = place.getCanonicalName();
        String canonicalName = normalizeOptional(request.canonicalName());
        String cityName = normalizeOptional(request.cityName());
        String country = normalizeOptional(request.country());
        String category = normalizeOptional(request.category());
        String address = normalizeOptional(request.address());

        if (category != null) {
            placeService.validateScopeAndCategory(place.getPlaceScope(), category);
        }

        UpdateWrapper<Place> update = new UpdateWrapper<Place>()
                .eq("id", place.getId())
                .isNull("deleted_at");
        boolean changed = false;
        if (canonicalName != null) {
            update.set("canonical_name", canonicalName);
            changed = true;
        }
        if (PlaceScope.DESTINATION.name().equals(place.getPlaceScope()) && canonicalName != null) {
            update.set("city_name", canonicalName);
            changed = true;
        } else if (cityName != null) {
            update.set("city_name", cityName);
            changed = true;
        }
        if (country != null) {
            update.set("country", country);
            changed = true;
        }
        if (category != null) {
            update.set("category", category);
            changed = true;
        }
        if (address != null) {
            update.set("address", address);
            changed = true;
        }
        if (request.latitude() != null) {
            update.set("latitude", request.latitude());
            changed = true;
        }
        if (request.longitude() != null) {
            update.set("longitude", request.longitude());
            changed = true;
        }
        if (!changed) {
            return toItem(place.getId());
        }
        update.set("updated_at", DateTimeUtil.nowUtc());
        placeMapper.update(null, update);

        boolean cascade = Boolean.TRUE.equals(request.cascadeCityRename());
        if (cascade
                && PlaceScope.DESTINATION.name().equals(place.getPlaceScope())
                && canonicalName != null
                && !canonicalName.equals(oldName)) {
            placeMapper.update(null, new UpdateWrapper<Place>()
                    .eq("place_scope", PlaceScope.POI.name())
                    .eq("city_name", oldName)
                    .eq("status", PlaceStatus.ACTIVE.name())
                    .isNull("deleted_at")
                    .set("city_name", canonicalName)
                    .set("updated_at", DateTimeUtil.nowUtc()));
        }
        return toItem(place.getId());
    }

    @Override
    @Transactional(readOnly = true)
    public PlaceAdminSearchItem getPlace(String placeId) {
        return toItem(requireActivePlace(placeId).getId());
    }

    private Place requireDestinationIfPresent(String destinationPlaceId) {
        if (!StringUtils.hasText(destinationPlaceId)) {
            return null;
        }
        return requireDestination(destinationPlaceId);
    }

    private Place requireDestination(String destinationPlaceId) {
        if (!StringUtils.hasText(destinationPlaceId)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请选择所属城市，否则探索城市页无法关联地点");
        }
        Place destination = requireActivePlace(destinationPlaceId.trim());
        if (!PlaceScope.DESTINATION.name().equals(destination.getPlaceScope())) {
            throw new BizException(ErrorCode.PLACE_INVALID_SCOPE_CATEGORY, "所属城市必须是 DESTINATION");
        }
        return destination;
    }

    private Place requireActivePlace(String placeId) {
        Place place = placeService.getPlaceById(placeId);
        if (place == null) {
            throw new NotFoundException(ErrorCode.PLACE_NOT_FOUND);
        }
        return place;
    }

    private Place findActiveDestinationByName(String name, String country) {
        QueryWrapper<Place> query = new QueryWrapper<Place>()
                .eq("place_scope", PlaceScope.DESTINATION.name())
                .eq("canonical_name", name)
                .eq("country", country)
                .eq("status", PlaceStatus.ACTIVE.name())
                .isNull("deleted_at")
                .last("LIMIT 1");
        return placeMapper.selectOne(query);
    }

    private void stampDestination(Place place, Place destination) {
        String cityName = destination.getCanonicalName();
        String country = destination.getCountry();
        boolean cityChanged = cityName != null && !cityName.equals(place.getCityName());
        boolean countryChanged = country != null && !country.equals(place.getCountry());
        if (!cityChanged && !countryChanged) {
            return;
        }
        UpdateWrapper<Place> update = new UpdateWrapper<Place>()
                .eq("id", place.getId())
                .isNull("deleted_at");
        if (cityChanged) {
            update.set("city_name", cityName);
            place.setCityName(cityName);
        }
        if (countryChanged) {
            update.set("country", country);
            place.setCountry(country);
        }
        update.set("updated_at", DateTimeUtil.nowUtc());
        placeMapper.update(null, update);
    }

    private PlaceAdminSearchItem toItem(String placeId) {
        PageResponse<PlaceAdminSearchItem> page = placeContentService.searchPlacesForAdmin(
                new PlaceAdminSearchQuery(null, placeId, null, null, null, null, 1, 1));
        if (page.list().isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "地点解析成功但读取条目失败，请重试");
        }
        return page.list().get(0);
    }

    private static String requireText(String value, String message) {
        String normalized = normalizeOptional(value);
        if (normalized == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, message);
        }
        return normalized;
    }

    private static String firstText(String primary, String fallback) {
        String normalized = normalizeOptional(primary);
        return normalized != null ? normalized : normalizeOptional(fallback);
    }

    private static String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return StringUtils.hasText(trimmed) ? trimmed : null;
    }
}
