package com.tracci.module.place.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tracci.common.context.LoginUserContext;
import com.tracci.common.response.ApiResponse;
import com.tracci.common.response.PageResponse;
import com.tracci.module.place.dto.request.PublishedPlaceListQuery;
import com.tracci.module.place.dto.response.PlaceDetailResponse;
import com.tracci.module.place.dto.response.PlaceListItemResponse;
import com.tracci.module.place.dto.response.PlaceUserContext;
import com.tracci.module.place.service.PlaceService;
import com.tracci.module.savedplace.service.SavedPlaceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/places")
@RequiredArgsConstructor
public class PlaceController {

    private static final String PRIVATE_REVALIDATE =
            "private, no-cache, max-age=0, must-revalidate";

    private final PlaceService placeService;
    private final SavedPlaceService savedPlaceService;
    private final ObjectMapper objectMapper;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<PlaceListItemResponse>>> listPublishedPlaces(
            @RequestParam(required = false) String cityName,
            @RequestParam(required = false) String placeScope,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        String userId = LoginUserContext.requireUserId();
        PageResponse<PlaceListItemResponse> result = placeService.listPublishedPlaces(
                new PublishedPlaceListQuery(cityName, placeScope, category, page, pageSize));
        List<String> placeIds = result.list().stream().map(PlaceListItemResponse::id).toList();
        Set<String> savedIds = savedPlaceService.findSavedPlaceIds(userId, placeIds);
        List<PlaceListItemResponse> items = result.list().stream()
                .map(item -> item.withIsSaved(savedIds.contains(item.id())))
                .toList();
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, PRIVATE_REVALIDATE)
                .body(ApiResponse.ok(PageResponse.of(items, result.total(), result.page(), result.pageSize())));
    }

    @GetMapping("/{placeId}")
    public ResponseEntity<ApiResponse<PlaceDetailResponse>> getPlaceDetail(
            @PathVariable String placeId,
            @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch) {
        String userId = LoginUserContext.requireUserId();
        PlaceDetailResponse base = placeService.getPlaceDetail(placeId);
        // 使用 canonical place id 组装私有上下文（MERGED 解析后 base.id 为权威 id）
        PlaceUserContext userContext = savedPlaceService.buildUserContext(userId, base.id());
        PlaceDetailResponse body = new PlaceDetailResponse(
                base.id(),
                base.name(),
                base.placeScope(),
                base.category(),
                base.address(),
                base.cityName(),
                base.country(),
                base.latitude(),
                base.longitude(),
                base.supportedUsages(),
                userContext,
                base.editorialContent(),
                base.contentMeta(),
                base.media()
        );

        String eTag = buildETag(userId, body);
        if (matchesIfNoneMatch(ifNoneMatch, eTag)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                    .header(HttpHeaders.CACHE_CONTROL, PRIVATE_REVALIDATE)
                    .eTag(eTag)
                    .build();
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, PRIVATE_REVALIDATE)
                .eTag(eTag)
                .body(ApiResponse.ok(body));
    }

    /**
     * ETag 含 userContext，必须按用户计算，禁止整响应做公共缓存。
     */
    private String buildETag(String userId, PlaceDetailResponse body) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(userId.getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            byte[] hash = digest.digest(objectMapper.writeValueAsBytes(body));
            return "\"" + HexFormat.of().formatHex(hash).substring(0, 32) + "\"";
        } catch (NoSuchAlgorithmException | JsonProcessingException e) {
            throw new IllegalStateException("无法生成地点详情 ETag", e);
        }
    }

    private static boolean matchesIfNoneMatch(String ifNoneMatch, String currentETag) {
        if (!StringUtils.hasText(ifNoneMatch) || !StringUtils.hasText(currentETag)) {
            return false;
        }
        String current = weakETagValue(currentETag);
        for (String part : ifNoneMatch.split(",")) {
            String trimmed = part.trim();
            if ("*".equals(trimmed) || current.equals(weakETagValue(trimmed))) {
                return true;
            }
        }
        return false;
    }

    private static String weakETagValue(String eTag) {
        String value = eTag.trim();
        if (value.startsWith("W/") || value.startsWith("w/")) {
            value = value.substring(2).trim();
        }
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            value = value.substring(1, value.length() - 1);
        }
        return value;
    }
}
