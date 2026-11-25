package com.tracci.module.placecontent.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tracci.common.context.LoginUserContext;
import com.tracci.common.exception.BizException;
import com.tracci.common.exception.NotFoundException;
import com.tracci.common.response.ErrorCode;
import com.tracci.common.response.PageResponse;
import com.tracci.common.util.DateTimeUtil;
import com.tracci.integration.cos.FileUrlResolver;
import com.tracci.module.file.entity.File;
import com.tracci.module.file.repository.FileMapper;
import com.tracci.module.place.entity.Place;
import com.tracci.module.place.entity.PlaceProviderRef;
import com.tracci.module.place.enums.PlaceCategory;
import com.tracci.module.place.enums.PlaceStatus;
import com.tracci.module.place.enums.PlaceScope;
import com.tracci.module.place.repository.PlaceMapper;
import com.tracci.module.place.repository.PlaceProviderRefMapper;
import com.tracci.module.placecontent.dto.request.ContentVersionCreateRequest;
import com.tracci.module.placecontent.dto.request.ContentVersionListQuery;
import com.tracci.module.placecontent.dto.request.ContentVersionUpdateRequest;
import com.tracci.module.placecontent.dto.request.PlaceAdminSearchQuery;
import com.tracci.module.placecontent.dto.request.SourceFactRequest;
import com.tracci.module.placecontent.dto.response.ContentMetaDto;
import com.tracci.module.placecontent.dto.response.ContentVersionResponse;
import com.tracci.module.placecontent.dto.response.EditorialContentDto;
import com.tracci.module.placecontent.dto.response.MediaAssetDto;
import com.tracci.module.placecontent.dto.response.PlaceAdminSearchItem;
import com.tracci.module.placecontent.dto.response.ProviderRefDto;
import com.tracci.module.placecontent.dto.response.PublishedContentDto;
import com.tracci.module.placecontent.dto.response.SourceFactsDto;
import com.tracci.module.placecontent.entity.PlaceContentSource;
import com.tracci.module.placecontent.entity.PlaceContentVersion;
import com.tracci.module.placecontent.entity.PlaceMediaAsset;
import com.tracci.module.placecontent.enums.ContentStatus;
import com.tracci.module.placecontent.enums.GenerationType;
import com.tracci.module.placecontent.enums.MediaStatus;
import com.tracci.module.placecontent.enums.SourceVerificationStatus;
import com.tracci.module.placecontent.repository.PlaceContentSourceMapper;
import com.tracci.module.placecontent.repository.PlaceContentVersionMapper;
import com.tracci.module.placecontent.repository.PlaceMediaAssetMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlaceContentServiceImpl implements PlaceContentService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String DEFAULT_LOCALE = "zh-CN";
    private static final Set<String> ALLOWED_SOURCE_TYPES = Set.of(
            "OFFICIAL_SITE", "GOVERNMENT", "CULTURAL_INSTITUTION",
            "MAP_PROVIDER", "EDITORIAL", "MANUAL"
    );
    /** 管理端搜索允许把业务 UUID 直接粘贴进 keyword，按 id 精确命中。 */
    private static final Pattern UUID_LIKE_PATTERN = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    private static final String PUBLISHED_EXISTS_SQL = """
            EXISTS (SELECT 1 FROM place_content_versions pcv
                    WHERE pcv.place_id = places.id
                      AND pcv.status = 'PUBLISHED'
                      AND pcv.deleted_at IS NULL)
            """;

    private final PlaceContentVersionMapper versionMapper;
    private final PlaceContentSourceMapper sourceMapper;
    private final PlaceMediaAssetMapper mediaAssetMapper;
    private final PlaceMapper placeMapper;
    private final PlaceProviderRefMapper providerRefMapper;
    private final FileMapper fileMapper;
    private final FileUrlResolver fileUrlResolver;

    @Override
    @Transactional
    public ContentVersionResponse createDraft(String placeId, ContentVersionCreateRequest request) {
        Place place = requireActivePlace(placeId);

        GenerationType generationType = parseGenerationType(request.generationType());
        String locale = normalizeLocale(request.locale());
        int nextVersionNo = resolveNextVersionNo(place.getId(), locale);

        PlaceContentVersion version = new PlaceContentVersion();
        version.setId(UUID.randomUUID().toString());
        version.setPlaceId(place.getId());
        version.setVersionNo(nextVersionNo);
        version.setLocale(locale);
        version.setIntroduction(normalizeOptional(request.introduction()));
        version.setHighlightsJson(normalizeJsonArray(request.highlightsJson(), "highlights"));
        version.setRecommendedDurationMinutes(request.recommendedDurationMinutes());
        version.setVisitTipsJson(normalizeJsonArray(request.visitTipsJson(), "visitTips"));
        version.setBestVisitTime(normalizeOptional(request.bestVisitTime()));
        version.setReservationTips(normalizeOptional(request.reservationTips()));
        version.setNotice(normalizeOptional(request.notice()));
        version.setGenerationType(generationType.name());
        version.setAiAssisted(Boolean.TRUE.equals(request.aiAssisted())
                || generationType == GenerationType.AI_ASSISTED);
        version.setStatus(ContentStatus.DRAFT.name());
        version.setNextReviewAt(request.nextReviewAt());
        if (StringUtils.hasText(request.rollbackFromVersionId())) {
            version.setRollbackFromVersionId(request.rollbackFromVersionId().trim());
        }
        version.setContentHash(computeContentHash(version));

        try {
            versionMapper.insert(version);
        } catch (DataIntegrityViolationException e) {
            throw new BizException(ErrorCode.PLACE_CONTENT_INVALID_STATE,
                    "创建内容版本失败，请检查 version_no 是否冲突后重试");
        }

        if (request.sources() != null && !request.sources().isEmpty()) {
            replaceSources(version.getId(), request.sources());
        }

        log.info("place_content draft created placeId={} versionId={} versionNo={} generationType={}",
                place.getId(), version.getId(), version.getVersionNo(), version.getGenerationType());
        return toResponseWithSources(version);
    }

    @Override
    @Transactional
    public ContentVersionResponse createGeneratedDraft(
            String placeId,
            ContentVersionCreateRequest request,
            String sourceDigest,
            String promptVersion,
            String modelProvider,
            String modelName,
            String workflowId,
            String generationIdempotencyKey,
            boolean regenerate) {
        Place lockedPlace = placeMapper.selectByIdForUpdate(placeId);
        if (lockedPlace == null
                || !PlaceStatus.ACTIVE.name().equals(lockedPlace.getStatus())) {
            throw new NotFoundException(ErrorCode.PLACE_CONTENT_NOT_FOUND, "关联地点不存在或不可用");
        }

        PlaceContentVersion idempotent = versionMapper.selectByGenerationIdempotencyKey(
                placeId, generationIdempotencyKey);
        if (idempotent != null) {
            return toResponseWithSources(idempotent);
        }

        if (!regenerate) {
            PlaceContentVersion existing = versionMapper.selectDraftBySourceDigest(
                    placeId, request.locale(), sourceDigest, promptVersion);
            if (existing != null) {
                return toResponseWithSources(existing);
            }
        } else {
            versionMapper.archiveDraftBySourceDigest(
                    placeId, request.locale(), sourceDigest, promptVersion, DateTimeUtil.nowUtc());
        }

        ContentVersionResponse created = createDraft(placeId, request);
        PlaceContentVersion version = requireVersion(created.id());
        version.setSourceDigest(sourceDigest);
        version.setPromptVersion(promptVersion);
        version.setModelProvider(modelProvider);
        version.setModelName(modelName);
        version.setSourceWorkflowId(workflowId);
        version.setGenerationIdempotencyKey(generationIdempotencyKey);
        versionMapper.updateById(version);

        log.info("place_content generated draft persisted placeId={} versionId={} workflowId={} regenerate={}",
                placeId, version.getId(), workflowId, regenerate);
        return toResponseWithSources(version);
    }

    @Override
    @Transactional
    public ContentVersionResponse updateDraft(String versionId, ContentVersionUpdateRequest request) {
        PlaceContentVersion version = requireVersion(versionId);
        assertStatus(version, ContentStatus.DRAFT, "只有草稿状态可编辑");

        if (request.introduction() != null) {
            version.setIntroduction(normalizeOptional(request.introduction()));
        }
        if (request.highlightsJson() != null) {
            version.setHighlightsJson(normalizeJsonArray(request.highlightsJson(), "highlights"));
        }
        if (request.recommendedDurationMinutes() != null) {
            version.setRecommendedDurationMinutes(request.recommendedDurationMinutes());
        }
        if (request.visitTipsJson() != null) {
            version.setVisitTipsJson(normalizeJsonArray(request.visitTipsJson(), "visitTips"));
        }
        if (request.bestVisitTime() != null) {
            version.setBestVisitTime(normalizeOptional(request.bestVisitTime()));
        }
        if (request.reservationTips() != null) {
            version.setReservationTips(normalizeOptional(request.reservationTips()));
        }
        if (request.notice() != null) {
            version.setNotice(normalizeOptional(request.notice()));
        }
        if (request.nextReviewAt() != null) {
            version.setNextReviewAt(request.nextReviewAt());
        }
        version.setContentHash(computeContentHash(version));
        versionMapper.updateById(version);

        if (request.sources() != null) {
            replaceSources(version.getId(), request.sources());
        }

        return toResponseWithSources(version);
    }

    @Override
    @Transactional
    public ContentVersionResponse submitReview(String versionId, String operatorId) {
        PlaceContentVersion version = requireVersion(versionId);
        assertStatus(version, ContentStatus.DRAFT, "只有草稿可提交审核");

        version.setStatus(ContentStatus.PENDING_REVIEW.name());
        // 提交人不等于最终审核人；审核字段在 publish 时写入
        versionMapper.updateById(version);

        log.info("place_content submit-review versionId={} operatorId={}", versionId, operatorId);
        return toResponseWithSources(version);
    }

    @Override
    @Transactional
    public ContentVersionResponse rejectReview(String versionId, String operatorId) {
        PlaceContentVersion version = requireVersion(versionId);
        assertStatus(version, ContentStatus.PENDING_REVIEW, "只有待审核版本可驳回");

        version.setStatus(ContentStatus.DRAFT.name());
        version.setReviewedBy(null);
        version.setReviewedAt(null);
        versionMapper.updateById(version);

        log.info("place_content reject-review versionId={} operatorId={}", versionId, operatorId);
        return toResponseWithSources(version);
    }

    @Override
    @Transactional
    public ContentVersionResponse publishVersion(
            String versionId,
            String reviewerId,
            String idempotencyKey,
            String requestId) {
        String normalizedKey = normalizePublishIdempotencyKey(idempotencyKey);
        String normalizedRequestId = normalizePublishRequestId(requestId);
        PlaceContentVersion version = lockVersion(versionId);

        // 已成功发布的 version 即使后来被归档/下线，同 key 重放也不再触发状态转换。
        if (normalizedKey.equals(version.getPublishIdempotencyKey())) {
            log.info("place_content publish replay requestId={} idempotencyKey={} versionId={} status={}",
                    normalizedRequestId, normalizedKey, versionId, version.getStatus());
            return toResponseWithSources(version);
        }
        assertStatus(version, ContentStatus.PENDING_REVIEW, "只有待审核版本可发布");

        String placeId = version.getPlaceId();
        String locale = version.getLocale();

        // 同一 Place 的发布共享一把行锁，串行化不同待审版本的发布事务。
        Place lockedPlace = placeMapper.selectByIdForUpdate(placeId);
        if (lockedPlace == null || lockedPlace.getDeletedAt() != null) {
            throw new NotFoundException(ErrorCode.PLACE_CONTENT_NOT_FOUND, "关联地点不存在或不可用");
        }

        PlaceContentVersion idempotentResult = versionMapper.selectByPublishIdempotencyKey(normalizedKey);
        if (idempotentResult != null) {
            if (versionId.equals(idempotentResult.getId())
                    && ContentStatus.PUBLISHED.name().equals(idempotentResult.getStatus())) {
                return toResponseWithSources(idempotentResult);
            }
            throw new BizException(ErrorCode.PLACE_CONTENT_ALREADY_PUBLISHED,
                    "Idempotency-Key 已用于其他发布请求");
        }

        assertSourcesPublishable(versionId);
        PlaceContentVersion previous = versionMapper.selectCurrentPublishedForUpdate(placeId, locale);
        LocalDateTime now = DateTimeUtil.nowUtc();

        // 归档当前 PUBLISHED（首次发布时影响 0 行）
        versionMapper.archiveCurrentPublished(placeId, locale, now);

        version.setStatus(ContentStatus.PUBLISHED.name());
        version.setReviewedBy(reviewerId);
        version.setReviewedAt(now);
        version.setPublishedAt(now);
        version.setArchivedAt(null);
        version.setPublishIdempotencyKey(normalizedKey);
        version.setPublishRequestId(normalizedRequestId);
        version.setPreviousPublishedVersionId(previous != null ? previous.getId() : null);
        version.setPreviousPublishedContentHash(previous != null ? previous.getContentHash() : null);
        if (version.getVerifiedAt() == null) {
            version.setVerifiedAt(now);
        }
        if (version.getNextReviewAt() == null) {
            // 默认半年复核（方案 §19）
            version.setNextReviewAt(now.plusMonths(6));
        }

        try {
            versionMapper.updateById(version);
        } catch (DataIntegrityViolationException e) {
            throw new BizException(ErrorCode.PLACE_CONTENT_ALREADY_PUBLISHED,
                    "该地点该语言已存在已发布版本，请重试");
        }

        log.info("place_content published requestId={} idempotencyKey={} operatorId={} placeId={} "
                        + "beforeVersionId={} beforeContentHash={} afterVersionId={} afterContentHash={}",
                normalizedRequestId, normalizedKey, reviewerId, placeId,
                previous != null ? previous.getId() : null,
                previous != null ? previous.getContentHash() : null,
                versionId, version.getContentHash());
        return toResponseWithSources(version);
    }

    @Override
    @Transactional
    public ContentVersionResponse markOutdated(String versionId, String operatorId) {
        PlaceContentVersion version = requireVersion(versionId);
        assertStatus(version, ContentStatus.PUBLISHED, "只有已发布版本可标记 OUTDATED");

        LocalDateTime now = DateTimeUtil.nowUtc();
        version.setStatus(ContentStatus.OUTDATED.name());
        version.setArchivedAt(now);
        versionMapper.updateById(version);

        log.info("place_content outdated versionId={} operatorId={}", versionId, operatorId);
        return toResponseWithSources(version);
    }

    @Override
    @Transactional
    public ContentVersionResponse archiveVersion(String versionId) {
        PlaceContentVersion version = requireVersion(versionId);
        ContentStatus current = ContentStatus.valueOf(version.getStatus());
        if (current == ContentStatus.PUBLISHED) {
            throw new BizException(ErrorCode.PLACE_CONTENT_INVALID_STATE,
                    "已发布版本请先 markOutdated 或由新版本发布顶替，不可直接归档");
        }
        if (current == ContentStatus.ARCHIVED) {
            throw new BizException(ErrorCode.PLACE_CONTENT_INVALID_STATE, "版本已归档");
        }

        version.setStatus(ContentStatus.ARCHIVED.name());
        version.setArchivedAt(DateTimeUtil.nowUtc());
        versionMapper.updateById(version);

        return toResponseWithSources(version);
    }

    @Override
    @Transactional
    public ContentVersionResponse cloneAsDraft(String versionId, String operatorId) {
        PlaceContentVersion source = requireVersion(versionId);
        ContentStatus status = ContentStatus.valueOf(source.getStatus());
        if (status != ContentStatus.PUBLISHED
                && status != ContentStatus.ARCHIVED
                && status != ContentStatus.OUTDATED) {
            throw new BizException(ErrorCode.PLACE_CONTENT_INVALID_STATE,
                    "仅已发布/已归档/已下线版本可克隆为回滚草稿");
        }

        List<PlaceContentSource> sources = listSources(source.getId());
        List<SourceFactRequest> sourceReqs = sources.stream()
                .map(s -> new SourceFactRequest(
                        s.getFactKey(),
                        s.getFactValue(),
                        s.getSourceType(),
                        s.getSourceTitle(),
                        s.getSourceUrl(),
                        s.getRetrievedAt(),
                        s.getExpiresAt(),
                        s.getVerificationStatus(),
                        s.getNote()
                ))
                .toList();

        ContentVersionCreateRequest request = new ContentVersionCreateRequest(
                source.getLocale(),
                GenerationType.ROLLBACK.name(),
                source.getAiAssisted(),
                source.getIntroduction(),
                source.getHighlightsJson(),
                source.getRecommendedDurationMinutes(),
                source.getVisitTipsJson(),
                source.getBestVisitTime(),
                source.getReservationTips(),
                source.getNotice(),
                sourceReqs,
                source.getId(),
                source.getNextReviewAt()
        );

        log.info("place_content clone-as-draft fromVersionId={} operatorId={}", versionId, operatorId);
        return createDraft(source.getPlaceId(), request);
    }

    @Override
    @Transactional(readOnly = true)
    public ContentVersionResponse getVersion(String versionId) {
        PlaceContentVersion version = requireVersion(versionId);
        return toResponseWithSources(version);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ContentVersionResponse> listVersions(ContentVersionListQuery query) {
        QueryWrapper<PlaceContentVersion> wrapper = new QueryWrapper<>();

        if (Boolean.TRUE.equals(query.reviewDue())) {
            // reviewDue 优先：仅 PUBLISHED 且 next_review_at 已到
            wrapper.eq("status", ContentStatus.PUBLISHED.name())
                    .le("next_review_at", DateTimeUtil.nowUtc());
        } else if (StringUtils.hasText(query.status())) {
            ContentStatus status = parseStatus(query.status());
            wrapper.eq("status", status.name());
        }
        if (StringUtils.hasText(query.placeId())) {
            wrapper.eq("place_id", query.placeId());
        }

        // Count SQL must not carry the list ordering. PostgreSQL rejects
        // SELECT COUNT(*) ... ORDER BY created_at because created_at is
        // neither grouped nor aggregated.
        long total = versionMapper.selectCount(wrapper);

        int page = Math.max(query.page(), 1);
        int pageSize = query.pageSize() > 0 ? Math.min(query.pageSize(), 100) : 20;
        wrapper.orderByDesc("created_at");
        wrapper.last("LIMIT " + pageSize + " OFFSET " + ((long) (page - 1) * pageSize));

        List<PlaceContentVersion> rows = versionMapper.selectList(wrapper);
        List<ContentVersionResponse> items = rows.stream().map(this::toResponse).toList();

        return PageResponse.of(items, total, page, pageSize);
    }

    @Override
    @Transactional(readOnly = true)
    public PublishedContentDto findPublishedContent(String placeId) {
        PlaceContentVersion version = versionMapper.selectOne(new QueryWrapper<PlaceContentVersion>()
                .eq("place_id", placeId)
                .eq("status", ContentStatus.PUBLISHED.name())
                .last("LIMIT 1"));
        if (version == null) {
            return null;
        }

        EditorialContentDto editorial = new EditorialContentDto(
                version.getVersionNo(),
                version.getIntroduction(),
                parseJsonStringList(version.getHighlightsJson()),
                version.getRecommendedDurationMinutes(),
                parseJsonStringList(version.getVisitTipsJson()),
                version.getBestVisitTime(),
                version.getReservationTips(),
                version.getNotice()
        );

        LocalDateTime now = DateTimeUtil.nowUtc();
        boolean reviewDue = version.getNextReviewAt() != null
                && !version.getNextReviewAt().isAfter(now);
        ContentMetaDto meta = new ContentMetaDto(
                Boolean.TRUE.equals(version.getAiAssisted()),
                Boolean.TRUE.equals(version.getAiAssisted()) ? "AI 辅助整理 · 已审核" : null,
                version.getVerifiedAt(),
                version.getNextReviewAt(),
                reviewDue
        );

        return new PublishedContentDto(editorial, meta);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MediaAssetDto> findApprovedMedia(String placeId) {
        if (!StringUtils.hasText(placeId)) {
            return List.of();
        }
        return resolveMedia(loadApprovedAssets(List.of(placeId))).stream()
                .map(ResolvedMedia::dto)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, MediaAssetDto> findCoverMediaByPlaceIds(Collection<String> placeIds) {
        if (placeIds == null || placeIds.isEmpty()) {
            return Map.of();
        }
        Map<String, MediaAssetDto> covers = new LinkedHashMap<>();
        for (ResolvedMedia item : resolveMedia(loadApprovedAssets(placeIds))) {
            covers.putIfAbsent(item.placeId(), item.dto());
        }
        return covers;
    }

    private List<PlaceMediaAsset> loadApprovedAssets(Collection<String> placeIds) {
        LocalDateTime now = DateTimeUtil.nowUtc();
        List<PlaceMediaAsset> assets = mediaAssetMapper.selectList(new QueryWrapper<PlaceMediaAsset>()
                .in("place_id", placeIds)
                .eq("status", MediaStatus.APPROVED.name())
                .and(w -> w.isNull("rights_expires_at").or().gt("rights_expires_at", now))
                .orderByAsc("sort_order")
                .orderByAsc("id"));
        return assets == null ? List.of() : assets;
    }

    private List<ResolvedMedia> resolveMedia(List<PlaceMediaAsset> assets) {
        List<String> fileIds = assets.stream()
                .map(PlaceMediaAsset::getFileId)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        Map<String, File> filesById = Map.of();
        if (!fileIds.isEmpty()) {
            Collection<File> files = fileMapper.selectBatchIds(fileIds);
            if (files != null) {
                filesById = files.stream()
                        .filter(Objects::nonNull)
                        .collect(Collectors.toMap(File::getId, file -> file, (left, right) -> left));
            }
        }

        List<ResolvedMedia> resolved = new ArrayList<>();
        for (PlaceMediaAsset asset : assets) {
            if (!StringUtils.hasText(asset.getFileId())) {
                continue;
            }
            MediaAssetDto dto = toMediaDto(asset, filesById.get(asset.getFileId()));
            if (dto != null) {
                resolved.add(new ResolvedMedia(asset.getPlaceId(), dto));
            }
        }
        return resolved;
    }

    private MediaAssetDto toMediaDto(PlaceMediaAsset asset, File file) {
        if (file == null) {
            return null;
        }
        var urls = fileUrlResolver.resolveUrls(file);
        String url = blankToNull(urls.url());
        String thumbnailUrl = blankToNull(urls.thumbnailUrl());
        String previewUrl = blankToNull(urls.previewUrl());
        if (url == null && thumbnailUrl == null && previewUrl == null) {
            return null;
        }
        return new MediaAssetDto(
                asset.getId(),
                url,
                thumbnailUrl,
                previewUrl,
                asset.getWidth(),
                asset.getHeight(),
                asset.getAltText(),
                asset.getAttribution()
        );
    }

    private static String blankToNull(String value) {
        return StringUtils.hasText(value) ? value : null;
    }

    private record ResolvedMedia(String placeId, MediaAssetDto dto) {
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<PlaceAdminSearchItem> searchPlacesForAdmin(PlaceAdminSearchQuery query) {
        QueryWrapper<Place> wrapper = new QueryWrapper<>();

        String keyword = StringUtils.hasText(query.keyword()) ? query.keyword().trim() : null;
        if (keyword != null) {
            wrapper.and(w -> {
                w.like("canonical_name", keyword).or().like("address", keyword);
                if (UUID_LIKE_PATTERN.matcher(keyword).matches()) {
                    w.or().eq("id", keyword);
                }
            });
        }
        if (StringUtils.hasText(query.cityName())) {
            wrapper.like("city_name", query.cityName().trim());
        }
        if (StringUtils.hasText(query.placeId())) {
            wrapper.eq("id", query.placeId().trim());
        }
        if (StringUtils.hasText(query.placeScope())) {
            wrapper.eq("place_scope", parsePlaceScope(query.placeScope()).name());
        }
        if (StringUtils.hasText(query.category())) {
            wrapper.eq("category", parsePlaceCategory(query.category()).name());
        }
        String contentState = normalizeContentState(query.contentState());
        if (contentState != null) {
            // contentState 只区分「已有/无 PUBLISHED 版本」；草稿与归档版本不参与过滤语义
            wrapper.apply("NONE".equals(contentState) ? "NOT " + PUBLISHED_EXISTS_SQL : PUBLISHED_EXISTS_SQL);
        }

        // Count SQL must not carry the list ordering (PostgreSQL 拒绝带 ORDER BY 的 COUNT)。
        long total = placeMapper.selectCount(wrapper);
        int page = Math.max(query.page(), 1);
        int pageSize = query.pageSize() > 0 ? Math.min(query.pageSize(), 50) : 20;
        // 名称排序让同名候选相邻，辅助人工消歧（APPLE/MANUAL 来源存在同名多 Place）
        wrapper.orderByAsc("canonical_name");
        wrapper.orderByAsc("id");
        wrapper.last("LIMIT " + pageSize + " OFFSET " + ((long) (page - 1) * pageSize));

        List<Place> places = placeMapper.selectList(wrapper);
        if (places.isEmpty()) {
            return PageResponse.of(List.of(), total, page, pageSize);
        }
        return PageResponse.of(assemblePlaceSearchItems(places), total, page, pageSize);
    }

    private List<PlaceAdminSearchItem> assemblePlaceSearchItems(List<Place> places) {
        List<String> placeIds = places.stream().map(Place::getId).toList();
        Map<String, List<PlaceProviderRef>> refsByPlace = providerRefMapper.selectList(
                        new QueryWrapper<PlaceProviderRef>().in("place_id", placeIds))
                .stream().collect(Collectors.groupingBy(PlaceProviderRef::getPlaceId));
        Map<String, List<PlaceContentVersion>> versionsByPlace = versionMapper.selectList(
                        new QueryWrapper<PlaceContentVersion>().in("place_id", placeIds))
                .stream().collect(Collectors.groupingBy(PlaceContentVersion::getPlaceId));

        List<PlaceAdminSearchItem> items = new ArrayList<>(places.size());
        for (Place place : places) {
            List<PlaceContentVersion> versions = versionsByPlace.getOrDefault(place.getId(), List.of());
            PlaceContentVersion published = versions.stream()
                    .filter(v -> ContentStatus.PUBLISHED.name().equals(v.getStatus()))
                    .max(Comparator.comparingInt(v -> v.getVersionNo() == null ? 0 : v.getVersionNo()))
                    .orElse(null);
            List<ProviderRefDto> providerRefs = refsByPlace.getOrDefault(place.getId(), List.of()).stream()
                    .map(r -> new ProviderRefDto(
                            r.getProvider(), r.getProviderPlaceId(), r.getIsPrimary(), r.getLastCheckedAt()))
                    .toList();
            items.add(new PlaceAdminSearchItem(
                    place.getId(),
                    place.getCanonicalName(),
                    place.getPlaceScope(),
                    place.getCategory(),
                    place.getCreatedSource(),
                    place.getStatus(),
                    place.getMergedIntoId(),
                    place.getAddress(),
                    place.getCityName(),
                    place.getCountry(),
                    place.getLatitude(),
                    place.getLongitude(),
                    providerRefs,
                    published != null,
                    published != null ? published.getId() : null,
                    published != null ? published.getVersionNo() : null,
                    versions.size()));
        }
        return items;
    }

    private PlaceScope parsePlaceScope(String raw) {
        try {
            return PlaceScope.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BizException(ErrorCode.PARAM_INVALID, "placeScope 仅支持 DESTINATION / POI");
        }
    }

    private PlaceCategory parsePlaceCategory(String raw) {
        try {
            return PlaceCategory.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BizException(ErrorCode.PARAM_INVALID, "category 仅支持 PlaceCategory 枚举值");
        }
    }

    private String normalizeContentState(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        if (!"PUBLISHED".equals(normalized) && !"NONE".equals(normalized)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "contentState 仅支持 PUBLISHED / NONE");
        }
        return normalized;
    }

    // ---- private helpers ----

    /**
     * 不依赖 PlaceService，避免与 PlaceServiceImpl 形成构造器循环依赖。
     * 支持 MERGED → canonical ACTIVE 解析。
     */
    private Place requireActivePlace(String placeId) {
        Place place = resolveActivePlace(placeId);
        if (place == null) {
            throw new NotFoundException(ErrorCode.PLACE_CONTENT_NOT_FOUND, "关联地点不存在或不可用");
        }
        return place;
    }

    private Place resolveActivePlace(String placeId) {
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

    private PlaceContentVersion requireVersion(String versionId) {
        PlaceContentVersion version = versionMapper.selectById(versionId);
        if (version == null || version.getDeletedAt() != null) {
            throw new NotFoundException(ErrorCode.PLACE_CONTENT_NOT_FOUND);
        }
        return version;
    }

    private PlaceContentVersion lockVersion(String versionId) {
        PlaceContentVersion locked = versionMapper.selectByIdForUpdate(versionId);
        if (locked == null || locked.getDeletedAt() != null) {
            throw new NotFoundException(ErrorCode.PLACE_CONTENT_NOT_FOUND);
        }
        return locked;
    }

    private void assertStatus(PlaceContentVersion version, ContentStatus expected, String message) {
        ContentStatus current = ContentStatus.valueOf(version.getStatus());
        if (current != expected) {
            throw new BizException(ErrorCode.PLACE_CONTENT_INVALID_STATE, message);
        }
    }

    private void assertSourcesPublishable(String versionId) {
        List<PlaceContentSource> sources = listSources(versionId);
        if (sources.isEmpty()) {
            throw new BizException(ErrorCode.PLACE_CONTENT_SOURCE_REQUIRED);
        }
        LocalDateTime now = DateTimeUtil.nowUtc();
        for (PlaceContentSource source : sources) {
            boolean verified = SourceVerificationStatus.VERIFIED.name()
                    .equals(source.getVerificationStatus());
            boolean hasAudit = StringUtils.hasText(source.getVerifiedBy())
                    && source.getVerifiedAt() != null;
            boolean expired = source.getExpiresAt() != null
                    && !source.getExpiresAt().isAfter(now);
            if (!verified || !hasAudit || expired) {
                throw new BizException(ErrorCode.PLACE_CONTENT_SOURCE_REQUIRED,
                        "发布前所有事实来源必须已核验、未过期并保留核验人和时间");
            }
        }
    }

    private void replaceSources(String versionId, List<SourceFactRequest> sources) {
        sourceMapper.softDeleteByVersionId(versionId, DateTimeUtil.nowUtc());
        if (sources == null || sources.isEmpty()) {
            return;
        }
        for (SourceFactRequest req : sources) {
            insertSource(versionId, req);
        }
    }

    private void insertSource(String versionId, SourceFactRequest req) {
        String sourceType = req.sourceType().trim().toUpperCase(Locale.ROOT);
        if (!ALLOWED_SOURCE_TYPES.contains(sourceType)) {
            throw new BizException(ErrorCode.PLACE_CONTENT_INVALID_STATE,
                    "sourceType 不合法: " + req.sourceType());
        }
        SourceVerificationStatus verification = SourceVerificationStatus.COLLECTED;
        if (StringUtils.hasText(req.verificationStatus())) {
            try {
                verification = SourceVerificationStatus.valueOf(
                        req.verificationStatus().trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new BizException(ErrorCode.PLACE_CONTENT_INVALID_STATE,
                        "verificationStatus 仅支持 COLLECTED / VERIFIED / REJECTED / EXPIRED");
            }
        }

        PlaceContentSource source = new PlaceContentSource();
        source.setId(UUID.randomUUID().toString());
        source.setContentVersionId(versionId);
        source.setFactKey(req.factKey().trim());
        source.setFactValue(req.factValue().trim());
        source.setSourceType(sourceType);
        source.setSourceTitle(normalizeOptional(req.sourceTitle()));
        source.setSourceUrl(normalizeOptional(req.sourceUrl()));
        source.setRetrievedAt(req.retrievedAt() != null ? req.retrievedAt() : DateTimeUtil.nowUtc());
        source.setExpiresAt(req.expiresAt());
        source.setVerificationStatus(verification.name());
        if (verification == SourceVerificationStatus.VERIFIED) {
            source.setVerifiedBy(LoginUserContext.requireUserId());
            source.setVerifiedAt(DateTimeUtil.nowUtc());
        }
        source.setNote(normalizeOptional(req.note()));
        source.setSourceHash(computeSourceHash(source));
        sourceMapper.insert(source);
    }

    private List<PlaceContentSource> listSources(String versionId) {
        return sourceMapper.selectList(new QueryWrapper<PlaceContentSource>()
                .eq("content_version_id", versionId)
                .orderByAsc("created_at"));
    }

    private int resolveNextVersionNo(String placeId, String locale) {
        Integer max = versionMapper.selectMaxVersionNo(placeId, locale);
        return (max == null ? 0 : max) + 1;
    }

    private GenerationType parseGenerationType(String raw) {
        if (!StringUtils.hasText(raw)) {
            return GenerationType.MANUAL;
        }
        try {
            return GenerationType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BizException(ErrorCode.PLACE_CONTENT_INVALID_STATE,
                    "generationType 仅支持 MANUAL / AI_ASSISTED / IMPORT / ROLLBACK");
        }
    }

    private ContentStatus parseStatus(String raw) {
        try {
            return ContentStatus.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BizException(ErrorCode.PLACE_CONTENT_INVALID_STATE,
                    "status 仅支持 DRAFT / PENDING_REVIEW / PUBLISHED / OUTDATED / ARCHIVED");
        }
    }

    private String normalizeLocale(String locale) {
        if (!StringUtils.hasText(locale)) {
            return DEFAULT_LOCALE;
        }
        return locale.trim();
    }

    private String normalizeJsonArray(String raw, String field) {
        if (!StringUtils.hasText(raw)) {
            return "[]";
        }
        try {
            JsonNode node = OBJECT_MAPPER.readTree(raw);
            if (!node.isArray()) {
                throw new BizException(ErrorCode.PLACE_CONTENT_INVALID_STATE, field + " 必须是 JSON 数组");
            }
            return raw.trim();
        } catch (JsonProcessingException e) {
            throw new BizException(ErrorCode.PLACE_CONTENT_INVALID_STATE, field + " 不是合法 JSON 数组");
        }
    }

    private String computeContentHash(PlaceContentVersion version) {
        String payload = String.join("|",
                nullToEmpty(version.getIntroduction()),
                nullToEmpty(version.getHighlightsJson()),
                nullToEmpty(version.getRecommendedDurationMinutes()),
                nullToEmpty(version.getVisitTipsJson()),
                nullToEmpty(version.getBestVisitTime()),
                nullToEmpty(version.getReservationTips()),
                nullToEmpty(version.getNotice())
        );
        return sha256(payload);
    }

    private String computeSourceHash(PlaceContentSource source) {
        String payload = String.join("|",
                nullToEmpty(source.getFactKey()),
                nullToEmpty(source.getFactValue()),
                nullToEmpty(source.getSourceType()),
                nullToEmpty(source.getSourceUrl())
        );
        return sha256(payload);
    }

    private String sha256(String payload) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 算法不可用", e);
        }
    }

    private String nullToEmpty(Object value) {
        return value == null ? "" : value.toString();
    }

    private String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return StringUtils.hasText(trimmed) ? trimmed : null;
    }

    private String normalizePublishIdempotencyKey(String value) {
        String normalized = normalizeOptional(value);
        if (normalized == null || normalized.length() < 8 || normalized.length() > 128) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "Idempotency-Key 长度需在 8-128 之间");
        }
        return normalized;
    }

    private String normalizePublishRequestId(String value) {
        String normalized = normalizeOptional(value);
        if (normalized == null) {
            return UUID.randomUUID().toString();
        }
        if (normalized.length() > 128) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "X-Request-Id 长度不能超过 128");
        }
        return normalized;
    }

    private ContentVersionResponse toResponse(PlaceContentVersion version) {
        return new ContentVersionResponse(
                version.getId(),
                version.getPlaceId(),
                version.getVersionNo(),
                version.getLocale(),
                version.getIntroduction(),
                parseJsonStringList(version.getHighlightsJson()),
                version.getRecommendedDurationMinutes(),
                parseJsonStringList(version.getVisitTipsJson()),
                version.getBestVisitTime(),
                version.getReservationTips(),
                version.getNotice(),
                version.getGenerationType(),
                Boolean.TRUE.equals(version.getAiAssisted()),
                version.getModelProvider(),
                version.getModelName(),
                version.getPromptVersion(),
                version.getStatus(),
                version.getReviewedBy(),
                version.getReviewedAt(),
                version.getVerifiedAt(),
                version.getNextReviewAt(),
                version.getPublishedAt(),
                version.getArchivedAt(),
                version.getContentHash(),
                version.getGenerationIdempotencyKey(),
                version.getSourceWorkflowId(),
                version.getPublishIdempotencyKey(),
                version.getPublishRequestId(),
                version.getPreviousPublishedVersionId(),
                version.getPreviousPublishedContentHash(),
                version.getRollbackFromVersionId(),
                null,
                version.getCreatedAt(),
                version.getUpdatedAt()
        );
    }

    private ContentVersionResponse toResponseWithSources(PlaceContentVersion version) {
        List<SourceFactsDto> sourceDtos = listSources(version.getId()).stream()
                .map(s -> new SourceFactsDto(
                        s.getId(),
                        s.getFactKey(),
                        s.getFactValue(),
                        s.getSourceType(),
                        s.getSourceTitle(),
                        s.getSourceUrl(),
                        s.getRetrievedAt(),
                        s.getExpiresAt(),
                        s.getVerificationStatus(),
                        s.getVerifiedBy(),
                        s.getVerifiedAt(),
                        s.getNote()
                ))
                .toList();

        ContentVersionResponse base = toResponse(version);
        return new ContentVersionResponse(
                base.id(), base.placeId(), base.versionNo(), base.locale(),
                base.introduction(), base.highlights(), base.recommendedDurationMinutes(),
                base.visitTips(), base.bestVisitTime(), base.reservationTips(), base.notice(),
                base.generationType(), base.aiAssisted(), base.modelProvider(), base.modelName(),
                base.promptVersion(), base.status(), base.reviewedBy(), base.reviewedAt(),
                base.verifiedAt(), base.nextReviewAt(), base.publishedAt(), base.archivedAt(),
                base.contentHash(), base.generationIdempotencyKey(), base.sourceWorkflowId(),
                base.publishIdempotencyKey(), base.publishRequestId(),
                base.previousPublishedVersionId(), base.previousPublishedContentHash(),
                base.rollbackFromVersionId(), sourceDtos,
                base.createdAt(), base.updatedAt()
        );
    }

    private List<String> parseJsonStringList(String json) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            List<String> parsed = OBJECT_MAPPER.readValue(json, new TypeReference<>() {
            });
            return parsed.isEmpty() ? null : parsed;
        } catch (JsonProcessingException e) {
            return null;
        }
    }
}
