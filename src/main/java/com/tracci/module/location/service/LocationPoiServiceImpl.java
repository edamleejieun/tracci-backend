package com.tracci.module.location.service;

import com.tracci.common.exception.BizException;
import com.tracci.common.response.ErrorCode;
import com.tracci.integration.amap.AmapProperties;
import com.tracci.module.location.dto.request.LocationPoiSearchReq;
import com.tracci.module.location.dto.response.LocationPoiDTO;
import com.tracci.module.location.dto.response.LocationPoiSearchResp;
import com.tracci.module.location.provider.PlaceSearchPage;
import com.tracci.module.location.provider.PlaceSearchProvider;
import com.tracci.module.location.provider.PlaceSearchProviderRouter;
import com.tracci.module.location.provider.PlaceSearchProviderType;
import com.tracci.module.location.provider.PlaceSearchQuery;
import com.tracci.module.location.provider.PlaceSearchResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class LocationPoiServiceImpl implements LocationPoiService {

    private final PlaceSearchProviderRouter placeSearchProviderRouter;
    private final AmapProperties amapProperties;

    @Override
    public LocationPoiSearchResp search(LocationPoiSearchReq req) {
        String keyword = normalizeKeyword(req.keyword());
        String city = normalizeOptional(req.city(), 50, "city 长度不能超过 50");
        int page = req.page() == null ? 1 : req.page();
        int size = req.size() == null ? amapProperties.getPoi().getDefaultPageSize() : req.size();
        size = Math.min(Math.max(size, 1), amapProperties.getPoi().getMaxPageSize());

        PlaceSearchProviderType providerType = placeSearchProviderRouter.resolveProviderType(req.clientPlatform());
        if (providerType == PlaceSearchProviderType.DISABLED) {
            return disabledResponse(page, size);
        }

        PlaceSearchProvider provider = placeSearchProviderRouter.provider(providerType);
        PlaceSearchQuery query = new PlaceSearchQuery(
                keyword,
                city,
                req.latitude(),
                req.longitude(),
                page,
                size,
                req.clientPlatform());
        PlaceSearchPage pageResult = provider.search(query);
        List<LocationPoiDTO> items = pageResult.items().stream()
                .map(this::toLocationPoi)
                .toList();
        boolean hasMore = page * size < pageResult.totalCount();
        return new LocationPoiSearchResp(items, page, size, hasMore, providerType.name(), null);
    }

    private LocationPoiSearchResp disabledResponse(int page, int size) {
        return new LocationPoiSearchResp(
                List.of(),
                page,
                size,
                false,
                PlaceSearchProviderType.DISABLED.name(),
                "PROVIDER_DISABLED");
    }

    private LocationPoiDTO toLocationPoi(PlaceSearchResult result) {
        Map<String, Object> metadata = result.providerMetadata();
        return new LocationPoiDTO(
                result.providerPlaceId(),
                result.name(),
                result.address(),
                result.latitude(),
                result.longitude(),
                metadataValue(metadata, "provinceName"),
                result.city(),
                metadataValue(metadata, "districtName"),
                metadataValue(metadata, "adcode"),
                metadataValue(metadata, "cityCode"),
                metadataValue(metadata, "typeCode"),
                metadataValue(metadata, "typeName"),
                result.provider()
        );
    }

    private String metadataValue(Map<String, Object> metadata, String key) {
        if (metadata == null || !metadata.containsKey(key)) {
            return null;
        }
        Object value = metadata.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private String normalizeKeyword(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            throw new BizException(ErrorCode.AUTH_PARAM_INVALID, "keyword 不能为空");
        }
        String value = keyword.trim();
        if (value.length() > 50) {
            throw new BizException(ErrorCode.AUTH_PARAM_INVALID, "keyword 长度不能超过 50");
        }
        return value;
    }

    private String normalizeOptional(String value, int maxLength, String message) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (!StringUtils.hasText(trimmed)) {
            return null;
        }
        if (trimmed.length() > maxLength) {
            throw new BizException(ErrorCode.AUTH_PARAM_INVALID, message);
        }
        return trimmed;
    }
}
