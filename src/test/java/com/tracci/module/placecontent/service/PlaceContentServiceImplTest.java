package com.tracci.module.placecontent.service;

import com.tracci.common.context.LoginUserContext;
import com.tracci.common.exception.BizException;
import com.tracci.common.exception.NotFoundException;
import com.tracci.common.response.ErrorCode;
import com.tracci.integration.cos.CiUrlBuilder;
import com.tracci.integration.cos.FileUrlResolver;
import com.tracci.module.file.entity.File;
import com.tracci.module.file.repository.FileMapper;
import com.tracci.module.place.entity.Place;
import com.tracci.module.place.enums.PlaceStatus;
import com.tracci.module.place.repository.PlaceMapper;
import com.tracci.module.place.repository.PlaceProviderRefMapper;
import com.tracci.module.placecontent.dto.request.ContentVersionCreateRequest;
import com.tracci.module.placecontent.dto.request.ContentVersionUpdateRequest;
import com.tracci.module.placecontent.dto.request.SourceFactRequest;
import com.tracci.module.placecontent.dto.response.MediaAssetDto;
import com.tracci.module.placecontent.entity.PlaceContentSource;
import com.tracci.module.placecontent.entity.PlaceContentVersion;
import com.tracci.module.placecontent.entity.PlaceMediaAsset;
import com.tracci.module.placecontent.enums.ContentStatus;
import com.tracci.module.placecontent.enums.GenerationType;
import com.tracci.module.placecontent.repository.PlaceContentSourceMapper;
import com.tracci.module.placecontent.repository.PlaceContentVersionMapper;
import com.tracci.module.placecontent.repository.PlaceMediaAssetMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PlaceContentServiceImplTest {

    @Mock
    private PlaceContentVersionMapper versionMapper;

    @Mock
    private PlaceContentSourceMapper sourceMapper;

    @Mock
    private PlaceMediaAssetMapper mediaAssetMapper;

    @Mock
    private PlaceMapper placeMapper;

    @Mock
    private PlaceProviderRefMapper providerRefMapper;

    @Mock
    private FileMapper fileMapper;

    @Mock
    private FileUrlResolver fileUrlResolver;

    private PlaceContentServiceImpl service;

    private static final String PLACE_ID = "place-001";
    private static final String VERSION_ID = "version-001";

    @BeforeEach
    void setUp() {
        service = new PlaceContentServiceImpl(
                versionMapper, sourceMapper, mediaAssetMapper, placeMapper, providerRefMapper,
                fileMapper, fileUrlResolver);
        LoginUserContext.setUserId("admin-001");
    }

    @AfterEach
    void tearDown() {
        LoginUserContext.clear();
    }

    @Test
    void createDraft_returnsDraftWithIncrementedVersionNoAndSources() {
        when(placeMapper.selectById(PLACE_ID)).thenReturn(activePlace());
        when(versionMapper.selectMaxVersionNo(PLACE_ID, "zh-CN")).thenReturn(null);
        when(versionMapper.insert(any(PlaceContentVersion.class))).thenReturn(1);
        when(sourceMapper.softDeleteByVersionId(any(), any())).thenReturn(0);
        when(sourceMapper.insert(any(PlaceContentSource.class))).thenReturn(1);
        when(sourceMapper.selectList(any())).thenReturn(List.of());

        var request = new ContentVersionCreateRequest(
                "zh-CN", "AI_ASSISTED", true,
                "介绍", "[\"亮点1\"]", 120, "[\"建议1\"]",
                "春秋", "提前预约", "注意安全",
                List.of(sourceFact()),
                null,
                null
        );

        var response = service.createDraft(PLACE_ID, request);

        assertThat(response.status()).isEqualTo("DRAFT");
        assertThat(response.versionNo()).isEqualTo(1);
        assertThat(response.generationType()).isEqualTo("AI_ASSISTED");
        assertThat(response.aiAssisted()).isTrue();
        assertThat(response.placeId()).isEqualTo(PLACE_ID);

        ArgumentCaptor<PlaceContentVersion> captor = ArgumentCaptor.forClass(PlaceContentVersion.class);
        verify(versionMapper).insert(captor.capture());
        assertThat(captor.getValue().getContentHash()).isNotBlank();
        ArgumentCaptor<PlaceContentSource> sourceCaptor =
                ArgumentCaptor.forClass(PlaceContentSource.class);
        verify(sourceMapper).insert(sourceCaptor.capture());
        assertThat(sourceCaptor.getValue().getVerifiedBy()).isEqualTo("admin-001");
        assertThat(sourceCaptor.getValue().getVerifiedAt()).isNotNull();
    }

    @Test
    void createDraft_incrementsFromExistingMax() {
        when(placeMapper.selectById(PLACE_ID)).thenReturn(activePlace());
        when(versionMapper.selectMaxVersionNo(PLACE_ID, "zh-CN")).thenReturn(3);
        when(versionMapper.insert(any(PlaceContentVersion.class))).thenReturn(1);
        when(sourceMapper.selectList(any())).thenReturn(List.of());

        var request = emptyCreateRequest();
        var response = service.createDraft(PLACE_ID, request);

        assertThat(response.versionNo()).isEqualTo(4);
    }

    @Test
    void createDraft_resolvesMergedPlaceToCanonical() {
        Place merged = new Place();
        merged.setId("merged-id");
        merged.setStatus(PlaceStatus.MERGED.name());
        merged.setMergedIntoId(PLACE_ID);

        when(placeMapper.selectById("merged-id")).thenReturn(merged);
        when(placeMapper.selectById(PLACE_ID)).thenReturn(activePlace());
        when(versionMapper.selectMaxVersionNo(PLACE_ID, "zh-CN")).thenReturn(null);
        when(versionMapper.insert(any(PlaceContentVersion.class))).thenReturn(1);
        when(sourceMapper.selectList(any())).thenReturn(List.of());

        var response = service.createDraft("merged-id", emptyCreateRequest());

        assertThat(response.placeId()).isEqualTo(PLACE_ID);
    }

    @Test
    void createDraft_throwsWhenPlaceNotActive() {
        Place place = new Place();
        place.setId(PLACE_ID);
        place.setStatus(PlaceStatus.DEPRECATED.name());
        when(placeMapper.selectById(PLACE_ID)).thenReturn(place);

        assertThatThrownBy(() -> service.createDraft(PLACE_ID, emptyCreateRequest()))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void createDraft_rejectsNonArrayHighlightsJson() {
        when(placeMapper.selectById(PLACE_ID)).thenReturn(activePlace());

        var request = new ContentVersionCreateRequest(
                null, null, null, null, "{\"a\":1}", null, null, null, null, null, null, null, null);

        assertThatThrownBy(() -> service.createDraft(PLACE_ID, request))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getErrorCode())
                .isEqualTo(ErrorCode.PLACE_CONTENT_INVALID_STATE);
    }

    @Test
    void createGeneratedDraft_locksPlaceAndPersistsGenerationTrace() {
        when(placeMapper.selectByIdForUpdate(PLACE_ID)).thenReturn(activePlace());
        when(placeMapper.selectById(PLACE_ID)).thenReturn(activePlace());
        when(versionMapper.selectByGenerationIdempotencyKey(PLACE_ID, "request-key"))
                .thenReturn(null);
        when(versionMapper.selectDraftBySourceDigest(PLACE_ID, "zh-CN", "digest", "prompt-v1"))
                .thenReturn(null);
        when(versionMapper.selectMaxVersionNo(PLACE_ID, "zh-CN")).thenReturn(null);
        AtomicReference<PlaceContentVersion> inserted = new AtomicReference<>();
        when(versionMapper.insert(any(PlaceContentVersion.class))).thenAnswer(invocation -> {
            inserted.set(invocation.getArgument(0));
            return 1;
        });
        when(versionMapper.selectById(anyString())).thenAnswer(invocation -> inserted.get());
        when(versionMapper.updateById(any(PlaceContentVersion.class))).thenReturn(1);
        when(sourceMapper.selectList(any())).thenReturn(List.of());

        var request = new ContentVersionCreateRequest(
                "zh-CN", "AI_ASSISTED", true, "介绍", "[]", null, "[]",
                null, null, null, null, null, null);

        var response = service.createGeneratedDraft(
                PLACE_ID, request, "digest", "prompt-v1", "provider", "model",
                "workflow-1", "request-key", false);

        assertThat(response.status()).isEqualTo("DRAFT");
        assertThat(inserted.get().getSourceDigest()).isEqualTo("digest");
        assertThat(inserted.get().getGenerationIdempotencyKey()).isEqualTo("request-key");
        assertThat(inserted.get().getSourceWorkflowId()).isEqualTo("workflow-1");
        assertThat(response.generationIdempotencyKey()).isEqualTo("request-key");
        assertThat(response.sourceWorkflowId()).isEqualTo("workflow-1");
        verify(placeMapper).selectByIdForUpdate(PLACE_ID);
    }

    @Test
    void createGeneratedDraft_reusesSameRequestInsideLockedTransaction() {
        PlaceContentVersion existing = draftVersion();
        when(placeMapper.selectByIdForUpdate(PLACE_ID)).thenReturn(activePlace());
        when(versionMapper.selectByGenerationIdempotencyKey(PLACE_ID, "request-key"))
                .thenReturn(existing);
        when(sourceMapper.selectList(any())).thenReturn(List.of());

        var response = service.createGeneratedDraft(
                PLACE_ID, emptyCreateRequest(), "digest", "prompt-v1", null, null,
                "workflow-1", "request-key", true);

        assertThat(response.id()).isEqualTo(VERSION_ID);
        verify(versionMapper, never()).insert(any(PlaceContentVersion.class));
        verify(versionMapper, never()).archiveDraftBySourceDigest(
                anyString(), anyString(), anyString(), anyString(), any());
    }

    @Test
    void updateDraft_onlyAllowsDraftStatus() {
        PlaceContentVersion version = draftVersion();
        version.setStatus(ContentStatus.PENDING_REVIEW.name());
        when(versionMapper.selectById(VERSION_ID)).thenReturn(version);

        var request = new ContentVersionUpdateRequest("新介绍", null, null, null, null, null, null, null, null);

        assertThatThrownBy(() -> service.updateDraft(VERSION_ID, request))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getErrorCode())
                .isEqualTo(ErrorCode.PLACE_CONTENT_INVALID_STATE);
    }

    @Test
    void submitReview_transitionsDraftToPendingWithoutReviewerStamp() {
        PlaceContentVersion version = draftVersion();
        when(versionMapper.selectById(VERSION_ID)).thenReturn(version);
        when(versionMapper.updateById(any(PlaceContentVersion.class))).thenReturn(1);
        when(sourceMapper.selectList(any())).thenReturn(List.of());

        var response = service.submitReview(VERSION_ID, "operator-001");

        assertThat(response.status()).isEqualTo("PENDING_REVIEW");
        assertThat(response.reviewedBy()).isNull();
    }

    @Test
    void rejectReview_returnsToDraft() {
        PlaceContentVersion version = pendingVersion();
        when(versionMapper.selectById(VERSION_ID)).thenReturn(version);
        when(versionMapper.updateById(any(PlaceContentVersion.class))).thenReturn(1);
        when(sourceMapper.selectList(any())).thenReturn(List.of());

        var response = service.rejectReview(VERSION_ID, "operator-001");

        assertThat(response.status()).isEqualTo("DRAFT");
    }

    @Test
    void publishVersion_archivesOldAndPublishesNew() {
        PlaceContentVersion version = pendingVersion();
        PlaceContentVersion previous = new PlaceContentVersion();
        previous.setId("version-old");
        previous.setContentHash("hash-old");
        when(versionMapper.selectByIdForUpdate(VERSION_ID)).thenReturn(version);
        when(placeMapper.selectByIdForUpdate(PLACE_ID)).thenReturn(activePlace());
        when(versionMapper.selectCurrentPublishedForUpdate(PLACE_ID, "zh-CN")).thenReturn(previous);
        when(versionMapper.archiveCurrentPublished(eq(PLACE_ID), eq("zh-CN"), any())).thenReturn(0);
        when(versionMapper.updateById(any(PlaceContentVersion.class))).thenReturn(1);
        when(sourceMapper.selectList(any())).thenReturn(List.of(verifiedSource()));

        var response = service.publishVersion(
                VERSION_ID, "reviewer-001", "publish-key-001", "request-001");

        assertThat(response.status()).isEqualTo("PUBLISHED");
        assertThat(response.publishedAt()).isNotNull();
        assertThat(response.reviewedBy()).isEqualTo("reviewer-001");
        assertThat(response.nextReviewAt()).isNotNull();
        assertThat(response.publishIdempotencyKey()).isEqualTo("publish-key-001");
        assertThat(response.publishRequestId()).isEqualTo("request-001");
        assertThat(response.previousPublishedVersionId()).isEqualTo("version-old");
        assertThat(response.previousPublishedContentHash()).isEqualTo("hash-old");
        verify(versionMapper).archiveCurrentPublished(eq(PLACE_ID), eq("zh-CN"), any());
        var inOrder = inOrder(versionMapper, placeMapper, sourceMapper);
        inOrder.verify(versionMapper).selectByIdForUpdate(VERSION_ID);
        inOrder.verify(placeMapper).selectByIdForUpdate(PLACE_ID);
        inOrder.verify(versionMapper).selectByPublishIdempotencyKey("publish-key-001");
        inOrder.verify(sourceMapper).selectList(any());
        inOrder.verify(versionMapper).selectCurrentPublishedForUpdate(PLACE_ID, "zh-CN");
        inOrder.verify(versionMapper).archiveCurrentPublished(eq(PLACE_ID), eq("zh-CN"), any());
        inOrder.verify(versionMapper).updateById(any(PlaceContentVersion.class));
    }

    @Test
    void publishVersion_rejectsInvalidIdempotencyKeyBeforeLockingRows() {
        assertThatThrownBy(() -> service.publishVersion(
                VERSION_ID, "reviewer-001", "short", "request-001"))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getErrorCode())
                .isEqualTo(ErrorCode.PARAM_INVALID);

        verifyNoInteractions(versionMapper, placeMapper, sourceMapper);
    }

    @Test
    void publishVersion_sameSuccessfulKey_isIdempotentReplay() {
        PlaceContentVersion version = pendingVersion();
        version.setStatus(ContentStatus.PUBLISHED.name());
        version.setPublishIdempotencyKey("publish-key-001");
        version.setPublishRequestId("request-original");
        when(versionMapper.selectByIdForUpdate(VERSION_ID)).thenReturn(version);
        when(sourceMapper.selectList(any())).thenReturn(List.of(verifiedSource()));

        var response = service.publishVersion(
                VERSION_ID, "reviewer-001", "publish-key-001", "request-retry");

        assertThat(response.status()).isEqualTo("PUBLISHED");
        assertThat(response.publishRequestId()).isEqualTo("request-original");
        verify(versionMapper, never()).archiveCurrentPublished(anyString(), anyString(), any());
        verify(versionMapper, never()).updateById(any(PlaceContentVersion.class));
        verifyNoInteractions(placeMapper);
    }

    @Test
    void publishVersion_sameSuccessfulKey_afterLaterArchive_remainsNoOp() {
        PlaceContentVersion version = pendingVersion();
        version.setStatus(ContentStatus.ARCHIVED.name());
        version.setPublishIdempotencyKey("publish-key-001");
        version.setPublishRequestId("request-original");
        when(versionMapper.selectByIdForUpdate(VERSION_ID)).thenReturn(version);
        when(sourceMapper.selectList(any())).thenReturn(List.of(verifiedSource()));

        var response = service.publishVersion(
                VERSION_ID, "reviewer-001", "publish-key-001", "request-retry");

        assertThat(response.status()).isEqualTo("ARCHIVED");
        verify(versionMapper, never()).archiveCurrentPublished(anyString(), anyString(), any());
        verify(versionMapper, never()).updateById(any(PlaceContentVersion.class));
        verifyNoInteractions(placeMapper);
    }

    @Test
    void publishVersion_rejectsKeyAlreadyUsedByAnotherVersion() {
        PlaceContentVersion version = pendingVersion();
        PlaceContentVersion existing = new PlaceContentVersion();
        existing.setId("version-other");
        existing.setStatus(ContentStatus.PUBLISHED.name());
        when(versionMapper.selectByIdForUpdate(VERSION_ID)).thenReturn(version);
        when(placeMapper.selectByIdForUpdate(PLACE_ID)).thenReturn(activePlace());
        when(versionMapper.selectByPublishIdempotencyKey("publish-key-001")).thenReturn(existing);

        assertThatThrownBy(() -> service.publishVersion(
                VERSION_ID, "reviewer-001", "publish-key-001", "request-001"))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getErrorCode())
                .isEqualTo(ErrorCode.PLACE_CONTENT_ALREADY_PUBLISHED);

        verify(versionMapper, never()).archiveCurrentPublished(anyString(), anyString(), any());
    }

    @Test
    void publishVersion_throwsWhenNoSources() {
        PlaceContentVersion version = pendingVersion();
        when(versionMapper.selectByIdForUpdate(VERSION_ID)).thenReturn(version);
        when(placeMapper.selectByIdForUpdate(PLACE_ID)).thenReturn(activePlace());
        when(sourceMapper.selectList(any())).thenReturn(List.of());

        assertThatThrownBy(() -> service.publishVersion(
                VERSION_ID, "reviewer-001", "publish-key-001", "request-001"))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getErrorCode())
                .isEqualTo(ErrorCode.PLACE_CONTENT_SOURCE_REQUIRED);
    }

    @Test
    void publishVersion_throwsWhenNotPendingReview() {
        PlaceContentVersion version = draftVersion();
        when(versionMapper.selectByIdForUpdate(VERSION_ID)).thenReturn(version);

        assertThatThrownBy(() -> service.publishVersion(
                VERSION_ID, "reviewer-001", "publish-key-001", "request-001"))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getErrorCode())
                .isEqualTo(ErrorCode.PLACE_CONTENT_INVALID_STATE);
    }

    @Test
    void publishVersion_rejectsUnverifiedSource() {
        PlaceContentVersion version = pendingVersion();
        PlaceContentSource source = verifiedSource();
        source.setVerificationStatus("REJECTED");
        when(versionMapper.selectByIdForUpdate(VERSION_ID)).thenReturn(version);
        when(placeMapper.selectByIdForUpdate(PLACE_ID)).thenReturn(activePlace());
        when(sourceMapper.selectList(any())).thenReturn(List.of(source));

        assertThatThrownBy(() -> service.publishVersion(
                VERSION_ID, "reviewer-001", "publish-key-001", "request-001"))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getErrorCode())
                .isEqualTo(ErrorCode.PLACE_CONTENT_SOURCE_REQUIRED);
    }

    @Test
    void markOutdated_onlyAllowsPublished() {
        PlaceContentVersion version = draftVersion();
        when(versionMapper.selectById(VERSION_ID)).thenReturn(version);

        assertThatThrownBy(() -> service.markOutdated(VERSION_ID, "op"))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getErrorCode())
                .isEqualTo(ErrorCode.PLACE_CONTENT_INVALID_STATE);
    }

    @Test
    void markOutdated_hidesPublishedContent() {
        PlaceContentVersion version = new PlaceContentVersion();
        version.setId(VERSION_ID);
        version.setPlaceId(PLACE_ID);
        version.setLocale("zh-CN");
        version.setStatus(ContentStatus.PUBLISHED.name());
        version.setDeletedAt(null);
        when(versionMapper.selectById(VERSION_ID)).thenReturn(version);
        when(versionMapper.updateById(any(PlaceContentVersion.class))).thenReturn(1);
        when(sourceMapper.selectList(any())).thenReturn(List.of());

        var response = service.markOutdated(VERSION_ID, "op");

        assertThat(response.status()).isEqualTo("OUTDATED");
    }

    @Test
    void archiveVersion_throwsForPublishedVersion() {
        PlaceContentVersion version = new PlaceContentVersion();
        version.setId(VERSION_ID);
        version.setStatus(ContentStatus.PUBLISHED.name());
        version.setDeletedAt(null);
        when(versionMapper.selectById(VERSION_ID)).thenReturn(version);

        assertThatThrownBy(() -> service.archiveVersion(VERSION_ID))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getErrorCode())
                .isEqualTo(ErrorCode.PLACE_CONTENT_INVALID_STATE);
    }

    @Test
    void cloneAsDraft_fromPublished() {
        PlaceContentVersion published = new PlaceContentVersion();
        published.setId(VERSION_ID);
        published.setPlaceId(PLACE_ID);
        published.setLocale("zh-CN");
        published.setStatus(ContentStatus.PUBLISHED.name());
        published.setIntroduction("intro");
        published.setHighlightsJson("[\"h\"]");
        published.setVisitTipsJson("[]");
        published.setAiAssisted(false);
        published.setDeletedAt(null);

        when(versionMapper.selectById(VERSION_ID)).thenReturn(published);
        when(sourceMapper.selectList(any())).thenReturn(List.of());
        when(placeMapper.selectById(PLACE_ID)).thenReturn(activePlace());
        when(versionMapper.selectMaxVersionNo(PLACE_ID, "zh-CN")).thenReturn(3);
        when(versionMapper.insert(any(PlaceContentVersion.class))).thenReturn(1);

        var response = service.cloneAsDraft(VERSION_ID, "op");

        assertThat(response.versionNo()).isEqualTo(4);
        assertThat(response.generationType()).isEqualTo(GenerationType.ROLLBACK.name());
        assertThat(response.status()).isEqualTo("DRAFT");

        ArgumentCaptor<PlaceContentVersion> captor = ArgumentCaptor.forClass(PlaceContentVersion.class);
        verify(versionMapper).insert(captor.capture());
        assertThat(captor.getValue().getRollbackFromVersionId()).isEqualTo(VERSION_ID);
    }

    @Test
    void getVersion_throwsNotFound() {
        when(versionMapper.selectById("unknown")).thenReturn(null);

        assertThatThrownBy(() -> service.getVersion("unknown"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void findPublishedContent_returnsNullWhenAbsent() {
        when(versionMapper.selectOne(any())).thenReturn(null);
        assertThat(service.findPublishedContent(PLACE_ID)).isNull();
    }

    @Test
    void findApprovedMedia_returnsEmptyWhenNoAssets() {
        assertThat(service.findApprovedMedia(PLACE_ID)).isEmpty();
    }

    @Test
    void findApprovedMedia_resolvesFileUrlsAndOmitsUnresolvable() {
        PlaceMediaAsset withFile = approvedAsset("media-1", PLACE_ID, "file-1", 0);
        PlaceMediaAsset sourceOnly = approvedAsset("media-2", PLACE_ID, null, 1);
        sourceOnly.setSourceReference("https://external.example/photo.jpg");
        when(mediaAssetMapper.selectList(any())).thenReturn(List.of(withFile, sourceOnly));

        File file = uploadedFile("file-1");
        when(fileMapper.selectBatchIds(List.of("file-1"))).thenReturn(List.of(file));
        when(fileUrlResolver.resolveUrls(file)).thenReturn(
                new CiUrlBuilder.CiUrls(
                        "https://cdn.example/orig.jpg",
                        "https://cdn.example/thumb.jpg",
                        "https://cdn.example/preview.jpg",
                        null, null, null, null, null));

        List<MediaAssetDto> media = service.findApprovedMedia(PLACE_ID);

        assertThat(media).hasSize(1);
        assertThat(media.get(0).id()).isEqualTo("media-1");
        assertThat(media.get(0).thumbnailUrl()).isEqualTo("https://cdn.example/thumb.jpg");
        assertThat(media.get(0).previewUrl()).isEqualTo("https://cdn.example/preview.jpg");
        assertThat(media.get(0).url()).isEqualTo("https://cdn.example/orig.jpg");
    }

    @Test
    void findCoverMediaByPlaceIds_picksFirstResolvablePerPlace() {
        PlaceMediaAsset expiredFirst = approvedAsset("media-a0", "place-a", "file-expired", 0);
        PlaceMediaAsset coverA = approvedAsset("media-a1", "place-a", "file-a", 1);
        PlaceMediaAsset coverB = approvedAsset("media-b0", "place-b", "file-b", 0);
        when(mediaAssetMapper.selectList(any())).thenReturn(List.of(expiredFirst, coverA, coverB));

        File expired = uploadedFile("file-expired");
        File fileA = uploadedFile("file-a");
        File fileB = uploadedFile("file-b");
        when(fileMapper.selectBatchIds(any())).thenReturn(List.of(expired, fileA, fileB));
        when(fileUrlResolver.resolveUrls(expired)).thenReturn(
                new CiUrlBuilder.CiUrls(null, null, null, null, null, null, null, null));
        when(fileUrlResolver.resolveUrls(fileA)).thenReturn(CiUrlBuilder.CiUrls.mirror("https://cdn.example/a.jpg"));
        when(fileUrlResolver.resolveUrls(fileB)).thenReturn(
                new CiUrlBuilder.CiUrls(
                        "https://cdn.example/b-orig.jpg",
                        null,
                        "https://cdn.example/b-preview.jpg",
                        null, null, null, null, null));

        var covers = service.findCoverMediaByPlaceIds(List.of("place-a", "place-b"));

        assertThat(covers).hasSize(2);
        assertThat(covers.get("place-a").id()).isEqualTo("media-a1");
        assertThat(covers.get("place-a").url()).isEqualTo("https://cdn.example/a.jpg");
        assertThat(covers.get("place-b").id()).isEqualTo("media-b0");
        assertThat(covers.get("place-b").previewUrl()).isEqualTo("https://cdn.example/b-preview.jpg");
        assertThat(covers.get("place-b").thumbnailUrl()).isNull();
    }

    // ---- helpers ----

    private Place activePlace() {
        Place place = new Place();
        place.setId(PLACE_ID);
        place.setStatus(PlaceStatus.ACTIVE.name());
        return place;
    }

    private PlaceContentVersion draftVersion() {
        PlaceContentVersion version = new PlaceContentVersion();
        version.setId(VERSION_ID);
        version.setPlaceId(PLACE_ID);
        version.setLocale("zh-CN");
        version.setStatus(ContentStatus.DRAFT.name());
        version.setGenerationType(GenerationType.MANUAL.name());
        version.setHighlightsJson("[]");
        version.setVisitTipsJson("[]");
        version.setDeletedAt(null);
        return version;
    }

    private PlaceContentVersion pendingVersion() {
        PlaceContentVersion version = draftVersion();
        version.setStatus(ContentStatus.PENDING_REVIEW.name());
        return version;
    }

    private static PlaceMediaAsset approvedAsset(String id, String placeId, String fileId, int sortOrder) {
        PlaceMediaAsset asset = new PlaceMediaAsset();
        asset.setId(id);
        asset.setPlaceId(placeId);
        asset.setFileId(fileId);
        asset.setSortOrder(sortOrder);
        asset.setStatus("APPROVED");
        asset.setAltText("alt");
        asset.setAttribution("attr");
        return asset;
    }

    private static File uploadedFile(String id) {
        File file = new File();
        file.setId(id);
        file.setStatus("UPLOADED");
        file.setObjectKey("places/" + id + ".jpg");
        return file;
    }

    private SourceFactRequest sourceFact() {
        return new SourceFactRequest(
                "history.summary",
                "founded 1406",
                "OFFICIAL_SITE",
                "故宫官网",
                "https://example.com",
                null,
                null,
                "VERIFIED",
                null
        );
    }

    private PlaceContentSource verifiedSource() {
        PlaceContentSource source = new PlaceContentSource();
        source.setId("source-001");
        source.setContentVersionId(VERSION_ID);
        source.setFactKey("history.summary");
        source.setFactValue("founded 1406");
        source.setSourceType("OFFICIAL_SITE");
        source.setVerificationStatus("VERIFIED");
        source.setVerifiedBy("admin-001");
        source.setVerifiedAt(java.time.LocalDateTime.now());
        return source;
    }

    private ContentVersionCreateRequest emptyCreateRequest() {
        return new ContentVersionCreateRequest(
                null, null, null, null, null, null, null, null, null, null, null, null, null);
    }
}
