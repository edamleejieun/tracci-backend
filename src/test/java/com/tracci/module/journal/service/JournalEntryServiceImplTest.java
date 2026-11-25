package com.tracci.module.journal.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tracci.common.context.LoginUserContext;
import com.tracci.common.exception.BizException;
import com.tracci.common.response.ErrorCode;
import com.tracci.common.util.DateTimeUtil;
import com.tracci.common.exception.ForbiddenException;
import com.tracci.common.exception.NotFoundException;
import com.tracci.integration.cos.CiUrlBuilder;
import com.tracci.integration.cos.FileUrlResolver;
import com.tracci.module.file.entity.File;
import com.tracci.module.file.repository.FileMapper;
import com.tracci.module.journal.dto.request.JournalEntryCreateReq;
import com.tracci.module.journal.dto.request.JournalEntryLocationReq;
import com.tracci.module.journal.dto.request.JournalEntryUpdateReq;
import com.tracci.module.journal.dto.response.JournalEntryResp;
import com.tracci.module.journal.entity.JournalEntry;
import com.tracci.module.journal.entity.JournalPhoto;
import com.tracci.module.journal.repository.JournalEntryMapper;
import com.tracci.module.journal.repository.JournalPhotoMapper;
import com.tracci.module.trip.entity.Trip;
import com.tracci.module.trip.entity.TripMember;
import com.tracci.module.trip.enums.TripPermission;
import com.tracci.module.trip.repository.TripMapper;
import com.tracci.module.trip.service.TripAccessService;
import com.tracci.module.trip.service.TripActivityLogService;
import com.tracci.module.user.repository.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static com.tracci.testsupport.TripAccessTestSupport.stubOwnerAccess;

@ExtendWith(MockitoExtension.class)
class JournalEntryServiceImplTest {

    @Mock
    private JournalEntryMapper journalEntryMapper;
    @Mock
    private JournalPhotoMapper journalPhotoMapper;
    @Mock
    private TripMapper tripMapper;
    @Mock
    private FileMapper fileMapper;
    @Mock
    private FileUrlResolver fileUrlResolver;
    @Mock
    private CiUrlBuilder ciUrlBuilder;
    @Mock
    private TripAccessService tripAccessService;
    @Mock
    private TripActivityLogService activityLogService;
    @Mock
    private UserMapper userMapper;

    @InjectMocks
    private JournalEntryServiceImpl journalEntryService;

    @BeforeEach
    void setUp() {
        stubOwnerAccess(tripAccessService, tripMapper);
        lenient().when(fileUrlResolver.resolve(any(File.class))).thenAnswer(inv -> {
            File f = inv.getArgument(0);
            return f != null ? f.getUrl() : null;
        });
        lenient().when(fileUrlResolver.resolveUrls(any())).thenAnswer(inv -> {
            File f = inv.getArgument(0);
            String url = f != null ? f.getUrl() : null;
            return CiUrlBuilder.CiUrls.mirror(url);
        });
        lenient().when(ciUrlBuilder.build(anyString(), anyString())).thenAnswer(inv -> {
            String url = inv.getArgument(0);
            return CiUrlBuilder.CiUrls.mirror(url);
        });
    }

    // ---- 1. 创建日志成功 ----

    @Test
    void createEntry_savesWithContentAndFileIds() {
        when(tripMapper.selectById("trip-1")).thenReturn(trip("u-1"));
        when(fileMapper.selectBatchIds(anyCollection())).thenReturn(List.of(
                uploadedFile("file-1", "u-1", "image/jpeg", "https://cdn.example.com/img1.jpg"),
                uploadedFile("file-2", "u-1", "image/png", "https://cdn.example.com/img2.jpg")
        ));
        doAnswer(invocation -> {
            JournalEntry entry = invocation.getArgument(0);
            entry.setId("entry-1");
            entry.setCreatedAt(LocalDateTime.of(2026, 7, 1, 12, 31));
            return 1;
        }).when(journalEntryMapper).insert(any(JournalEntry.class));

        LoginUserContext.setUserId("u-1");
        try {
            var resp = journalEntryService.createEntry("trip-1", new JournalEntryCreateReq(
                    " 浅草寺打卡 ",
                    location("浅草寺"),
                    List.of("file-1", "file-2"),
                    LocalDateTime.of(2026, 7, 1, 10, 30),
                    "happy",
                    true,
                    null
            ));

            assertEquals("entry-1", resp.id());
            assertEquals("trip-1", resp.tripId());
            assertEquals("浅草寺打卡", resp.content());
            assertEquals("浅草寺", resp.location().name());
            assertEquals(2, resp.photos().size());
            assertEquals("file-1", resp.photos().getFirst().fileId());
            assertEquals("https://cdn.example.com/img1.jpg", resp.photos().getFirst().url());
            assertEquals("https://cdn.example.com/img1.jpg", resp.photos().getFirst().thumbnailUrl());
            assertEquals(1080, resp.photos().getFirst().width());
            assertEquals(1440, resp.photos().getFirst().height());
            assertEquals(1, resp.photos().getFirst().sortOrder());
            assertEquals("file-2", resp.photos().get(1).fileId());
            assertEquals(2, resp.photos().get(1).sortOrder());
            assertEquals("happy", resp.mood());
            assertTrue(resp.isHighlight());
            assertNotNull(resp.occurredAt());
            assertNotNull(resp.createdAt());
        } finally {
            LoginUserContext.clear();
        }

        verify(journalEntryMapper).insert(any(JournalEntry.class));
        ArgumentCaptor<JournalPhoto> photoCaptor = ArgumentCaptor.forClass(JournalPhoto.class);
        verify(journalPhotoMapper, times(2)).insert(photoCaptor.capture());
        List<JournalPhoto> savedPhotos = photoCaptor.getAllValues();
        assertEquals("entry-1", savedPhotos.getFirst().getJournalEntryId());
        assertEquals("file-1", savedPhotos.getFirst().getFileId());
        assertEquals(1, savedPhotos.getFirst().getSortOrder());
    }

    @Test
    void createEntry_acceptsAvifFile() {
        when(tripMapper.selectById("trip-1")).thenReturn(trip("u-1"));
        when(fileMapper.selectBatchIds(anyCollection())).thenReturn(List.of(
                uploadedFile("file-1", "u-1", "image/avif", "https://cdn.example.com/img.avif")
        ));
        doAnswer(invocation -> {
            JournalEntry entry = invocation.getArgument(0);
            entry.setId("entry-1");
            entry.setCreatedAt(LocalDateTime.of(2026, 7, 1, 12, 31));
            return 1;
        }).when(journalEntryMapper).insert(any(JournalEntry.class));
        doAnswer(invocation -> {
            JournalPhoto photo = invocation.getArgument(0);
            photo.setId("photo-1");
            return 1;
        }).when(journalPhotoMapper).insert(any(JournalPhoto.class));

        LoginUserContext.setUserId("u-1");
        try {
            JournalEntryResp resp = journalEntryService.createEntry("trip-1",
                    new JournalEntryCreateReq("AVIF 图片", null,
                            List.of("file-1"),
                            LocalDateTime.of(2026, 7, 1, 10, 30), null, false, null));

            assertEquals(1, resp.photos().size());
            assertEquals("https://cdn.example.com/img.avif", resp.photos().getFirst().url());
        } finally {
            LoginUserContext.clear();
        }
    }

    @Test
    void createEntry_contentOnlyIsValid() {
        when(tripMapper.selectById("trip-1")).thenReturn(trip("u-1"));
        doAnswer(invocation -> {
            JournalEntry entry = invocation.getArgument(0);
            entry.setId("entry-2");
            entry.setCreatedAt(LocalDateTime.of(2026, 7, 1, 12, 31));
            return 1;
        }).when(journalEntryMapper).insert(any(JournalEntry.class));

        LoginUserContext.setUserId("u-1");
        try {
            var resp = journalEntryService.createEntry("trip-1", new JournalEntryCreateReq(
                    "只有文字没有图片",
                    null,
                    null,
                    LocalDateTime.of(2026, 7, 1, 10, 30),
                    null,
                    false,
                    null
            ));

            assertEquals("只有文字没有图片", resp.content());
            assertTrue(resp.photos().isEmpty());
            assertFalse(resp.isHighlight());
        } finally {
            LoginUserContext.clear();
        }
    }

    @Test
    void createEntry_imagesOnlyIsValid() {
        when(tripMapper.selectById("trip-1")).thenReturn(trip("u-1"));
        when(fileMapper.selectBatchIds(anyCollection())).thenReturn(List.of(
                uploadedFile("file-1", "u-1", "image/jpeg", "https://cdn.example.com/img.jpg")
        ));
        doAnswer(invocation -> {
            JournalEntry entry = invocation.getArgument(0);
            entry.setId("entry-3");
            entry.setCreatedAt(LocalDateTime.of(2026, 7, 1, 12, 31));
            return 1;
        }).when(journalEntryMapper).insert(any(JournalEntry.class));

        LoginUserContext.setUserId("u-1");
        try {
            var resp = journalEntryService.createEntry("trip-1", new JournalEntryCreateReq(
                    null,
                    location("东京塔"),
                    List.of("file-1"),
                    LocalDateTime.of(2026, 7, 1, 10, 30),
                    null,
                    false,
                    null
            ));

            assertEquals("东京塔", resp.location().name());
            assertEquals(1, resp.photos().size());
            assertEquals("file-1", resp.photos().getFirst().fileId());
        } finally {
            LoginUserContext.clear();
        }
    }

    @Test
    void createEntry_rejectsInvalidMood() {
        when(tripMapper.selectById("trip-1")).thenReturn(trip("u-1"));

        LoginUserContext.setUserId("u-1");
        try {
            assertThrows(BizException.class, () -> journalEntryService.createEntry("trip-1",
                    new JournalEntryCreateReq(
                            "心情不对",
                            null,
                            null,
                            LocalDateTime.of(2026, 7, 1, 10, 30),
                            "angry",
                            false,
                            null
                    )));
            verify(journalEntryMapper, never()).insert(any(JournalEntry.class));
        } finally {
            LoginUserContext.clear();
        }
    }

    @Test
    void updateEntry_clearsMoodWhenBlankStringProvided() {
        when(tripMapper.selectById("trip-1")).thenReturn(trip("u-1"));

        JournalEntry existing = entry("entry-1", "trip-1", "u-1");
        existing.setContent("有心情");
        existing.setMood("happy");
        when(journalEntryMapper.selectOne(any(QueryWrapper.class))).thenReturn(existing);
        when(journalEntryMapper.updateById(any(JournalEntry.class))).thenReturn(1);
        when(journalPhotoMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of());

        LoginUserContext.setUserId("u-1");
        try {
            var resp = journalEntryService.updateEntry("trip-1", "entry-1", new JournalEntryUpdateReq(
                    null, null, null, null, "   ", null, null));

            assertNull(resp.mood());
        } finally {
            LoginUserContext.clear();
        }
    }

    @Test
    void featurePhoto_marksSinglePhotoForWholeTrip() {
        when(tripMapper.selectById("trip-1")).thenReturn(trip("u-1"));
        JournalPhoto photo = new JournalPhoto();
        photo.setId("photo-1");
        photo.setTripId("trip-1");
        photo.setJournalEntryId("entry-parent");
        photo.setFileId("file-1");
        photo.setSortOrder(1);
        photo.setIsFeatured(false);
        when(journalPhotoMapper.selectOne(any(QueryWrapper.class))).thenReturn(photo);

        JournalEntry parentEntry = new JournalEntry();
        parentEntry.setId("entry-parent");
        parentEntry.setTripId("trip-1");
        parentEntry.setUserId("u-1");
        parentEntry.setVisibility("all");
        when(journalEntryMapper.selectOne(any(QueryWrapper.class))).thenReturn(parentEntry);

        when(journalPhotoMapper.updateFeatured("trip-1", "photo-1", true)).thenReturn(1);
        when(fileMapper.selectById("file-1")).thenReturn(
                uploadedFile("file-1", "u-1", "image/jpeg", "https://cdn.example.com/img.jpg"));

        LoginUserContext.setUserId("u-1");
        try {
            var response = journalEntryService.featurePhoto("trip-1", "photo-1");

            assertTrue(response.userFeatured());
            verify(tripMapper).lockByIdForUpdate("trip-1");
            verify(journalPhotoMapper).updateFeatured("trip-1", "photo-1", true);
            verify(activityLogService).record(
                    org.mockito.ArgumentMatchers.eq("trip-1"),
                    org.mockito.ArgumentMatchers.eq("u-1"),
                    // 夹具里是 owner 成员，memberId 由 TripAccess 给出，不再是 null
                    org.mockito.ArgumentMatchers.eq("owner-trip-1"),
                    org.mockito.ArgumentMatchers.eq("JOURNAL_PHOTO_FEATURED"),
                    org.mockito.ArgumentMatchers.eq("JOURNAL_PHOTO"),
                    org.mockito.ArgumentMatchers.eq("photo-1"),
                    any(),
                    anyString());
        } finally {
            LoginUserContext.clear();
        }
    }

    @Test
    void featurePhoto_rejectsTenthFeaturedPhoto() {
        when(tripMapper.selectById("trip-1")).thenReturn(trip("u-1"));
        JournalPhoto photo = new JournalPhoto();
        photo.setId("photo-10");
        photo.setTripId("trip-1");
        photo.setJournalEntryId("entry-parent");
        photo.setFileId("file-10");
        photo.setIsFeatured(false);
        when(journalPhotoMapper.selectOne(any(QueryWrapper.class))).thenReturn(photo);

        JournalEntry parentEntry = new JournalEntry();
        parentEntry.setId("entry-parent");
        parentEntry.setTripId("trip-1");
        parentEntry.setUserId("u-1");
        parentEntry.setVisibility("all");
        when(journalEntryMapper.selectOne(any(QueryWrapper.class))).thenReturn(parentEntry);

        when(fileMapper.selectById("file-10")).thenReturn(
                uploadedFile("file-10", "u-1", "image/jpeg", "https://cdn.example.com/img.jpg"));
        when(journalPhotoMapper.updateFeatured("trip-1", "photo-10", true)).thenReturn(0);

        LoginUserContext.setUserId("u-1");
        try {
            assertThrows(BizException.class, () -> journalEntryService.featurePhoto("trip-1", "photo-10"));
            verify(journalPhotoMapper).updateFeatured("trip-1", "photo-10", true);
        } finally {
            LoginUserContext.clear();
        }
    }

    @Test
    void featurePhoto_rejectsViewer() {
        doThrow(new ForbiddenException())
                .when(tripAccessService)
                .requirePermission("trip-1", "viewer", TripPermission.EDIT_CONTENT);

        LoginUserContext.setUserId("viewer");
        try {
            assertThrows(ForbiddenException.class,
                    () -> journalEntryService.featurePhoto("trip-1", "photo-1"));
            verify(journalPhotoMapper, never()).updateFeatured(anyString(), anyString(), anyBoolean());
        } finally {
            LoginUserContext.clear();
        }
    }

    // ---- 2. content 和 fileIds 同时为空时失败 ----

    @Test
    void createEntry_rejectsEmptyContentAndEmptyFileIds() {
        when(tripMapper.selectById("trip-1")).thenReturn(trip("u-1"));

        LoginUserContext.setUserId("u-1");
        try {
            assertThrows(BizException.class, () -> journalEntryService.createEntry("trip-1",
                    new JournalEntryCreateReq(
                            null, null,
                            List.of(),
                            LocalDateTime.of(2026, 7, 1, 10, 30),
                            null,
                            false,
                            null
                    )));
            verify(journalEntryMapper, never()).insert(any(JournalEntry.class));
        } finally {
            LoginUserContext.clear();
        }
    }

    @Test
    void createEntry_rejectsBlankContentAndNullFileIds() {
        when(tripMapper.selectById("trip-1")).thenReturn(trip("u-1"));

        LoginUserContext.setUserId("u-1");
        try {
            assertThrows(BizException.class, () -> journalEntryService.createEntry("trip-1",
                    new JournalEntryCreateReq(
                            "   ", null,
                            null,
                            LocalDateTime.of(2026, 7, 1, 10, 30),
                            null,
                            false,
                            null
                    )));
            verify(journalEntryMapper, never()).insert(any(JournalEntry.class));
        } finally {
            LoginUserContext.clear();
        }
    }

    // ---- 3. fileIds 超过 9 个时失败 ----

    @Test
    void createEntry_rejectsMoreThan9FileIds() {
        when(tripMapper.selectById("trip-1")).thenReturn(trip("u-1"));

        List<String> tenIds = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            tenIds.add("file-" + i);
        }

        LoginUserContext.setUserId("u-1");
        try {
            assertThrows(BizException.class, () -> journalEntryService.createEntry("trip-1",
                    new JournalEntryCreateReq(
                            "有 10 张图片",
                            null,
                            tenIds,
                            LocalDateTime.of(2026, 7, 1, 10, 30),
                            null,
                            false,
                            null
                    )));
            verify(journalEntryMapper, never()).insert(any(JournalEntry.class));
        } finally {
            LoginUserContext.clear();
        }
    }

    @Test
    void updateEntry_rejectsMoreThan9FileIds() {
        when(tripMapper.selectById("trip-1")).thenReturn(trip("u-1"));

        JournalEntry existing = entry("entry-1", "trip-1", "u-1");
        existing.setContent("原始内容");
        when(journalEntryMapper.selectOne(any(QueryWrapper.class))).thenReturn(existing);

        List<String> tenIds = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            tenIds.add("file-" + i);
        }

        LoginUserContext.setUserId("u-1");
        try {
            assertThrows(BizException.class, () -> journalEntryService.updateEntry("trip-1", "entry-1",
                    new JournalEntryUpdateReq(null, null,
                    tenIds, null, null, null, null)));
        } finally {
            LoginUserContext.clear();
        }
    }

    // ---- 绑定不存在 / 他人 / PENDING / 非图片文件 ----

    @Test
    void createEntry_rejectsNonExistentFileId() {
        when(tripMapper.selectById("trip-1")).thenReturn(trip("u-1"));
        when(fileMapper.selectBatchIds(anyCollection())).thenReturn(List.of());

        LoginUserContext.setUserId("u-1");
        try {
            assertThrows(BizException.class, () -> journalEntryService.createEntry("trip-1",
                    new JournalEntryCreateReq("日志", null,
                            List.of("non-existent-file"),
                            LocalDateTime.of(2026, 7, 1, 10, 30), null, false, null)));
            verify(journalEntryMapper, never()).insert(any(JournalEntry.class));
        } finally {
            LoginUserContext.clear();
        }
    }

    @Test
    void createEntry_rejectsOtherUserFileId() {
        when(tripMapper.selectById("trip-1")).thenReturn(trip("u-1"));
        when(fileMapper.selectBatchIds(anyCollection())).thenReturn(List.of(
                uploadedFile("file-1", "u-2", "image/jpeg", "https://cdn.example.com/img.jpg")
        ));

        LoginUserContext.setUserId("u-1");
        try {
            assertThrows(ForbiddenException.class, () -> journalEntryService.createEntry("trip-1",
                    new JournalEntryCreateReq("日志", null,
                            List.of("file-1"),
                            LocalDateTime.of(2026, 7, 1, 10, 30), null, false, null)));
            verify(journalEntryMapper, never()).insert(any(JournalEntry.class));
        } finally {
            LoginUserContext.clear();
        }
    }

    @Test
    void createEntry_rejectsPendingFile() {
        when(tripMapper.selectById("trip-1")).thenReturn(trip("u-1"));
        when(fileMapper.selectBatchIds(anyCollection())).thenReturn(List.of(
                pendingFile("file-1", "u-1", "image/jpeg")
        ));

        LoginUserContext.setUserId("u-1");
        try {
            assertThrows(BizException.class, () -> journalEntryService.createEntry("trip-1",
                    new JournalEntryCreateReq("日志", null,
                            List.of("file-1"),
                            LocalDateTime.of(2026, 7, 1, 10, 30), null, false, null)));
            verify(journalEntryMapper, never()).insert(any(JournalEntry.class));
        } finally {
            LoginUserContext.clear();
        }
    }

    @Test
    void createEntry_rejectsNonImageFile() {
        when(tripMapper.selectById("trip-1")).thenReturn(trip("u-1"));
        when(fileMapper.selectBatchIds(anyCollection())).thenReturn(List.of(
                uploadedFile("file-1", "u-1", "application/pdf", null)
        ));

        LoginUserContext.setUserId("u-1");
        try {
            assertThrows(BizException.class, () -> journalEntryService.createEntry("trip-1",
                    new JournalEntryCreateReq("日志", null,
                            List.of("file-1"),
                            LocalDateTime.of(2026, 7, 1, 10, 30), null, false, null)));
            verify(journalEntryMapper, never()).insert(any(JournalEntry.class));
        } finally {
            LoginUserContext.clear();
        }
    }

    @Test
    void createEntry_rejectsFileFromOtherTrip() {
        when(tripMapper.selectById("trip-1")).thenReturn(trip("u-1"));
        File file = uploadedFile("file-1", "u-1", "image/jpeg", "https://cdn.example.com/img.jpg");
        file.setBusinessId("trip-2");
        when(fileMapper.selectBatchIds(anyCollection())).thenReturn(List.of(file));

        LoginUserContext.setUserId("u-1");
        try {
            assertThrows(BizException.class, () -> journalEntryService.createEntry("trip-1",
                    new JournalEntryCreateReq("日志", null,
                            List.of("file-1"),
                            LocalDateTime.of(2026, 7, 1, 10, 30), null, false, null)));
            verify(journalEntryMapper, never()).insert(any(JournalEntry.class));
        } finally {
            LoginUserContext.clear();
        }
    }

    @Test
    void createEntry_rejectsNonJournalPhotoFile() {
        when(tripMapper.selectById("trip-1")).thenReturn(trip("u-1"));
        File file = uploadedFile("file-1", "u-1", "image/jpeg", "https://cdn.example.com/img.jpg");
        file.setBusinessType("TRIP_COVER");
        when(fileMapper.selectBatchIds(anyCollection())).thenReturn(List.of(file));

        LoginUserContext.setUserId("u-1");
        try {
            assertThrows(BizException.class, () -> journalEntryService.createEntry("trip-1",
                    new JournalEntryCreateReq("日志", null,
                            List.of("file-1"),
                            LocalDateTime.of(2026, 7, 1, 10, 30), null, false, null)));
            verify(journalEntryMapper, never()).insert(any(JournalEntry.class));
        } finally {
            LoginUserContext.clear();
        }
    }

    // ---- 4. 查询 trip 下日志时间轴 ----

    @Test
    void listEntries_returnsTimelineOrderedByOccurredAt() {
        when(tripMapper.selectById("trip-1")).thenReturn(trip("u-1"));

        JournalEntry e1 = entry("entry-1", "trip-1", "u-1");
        e1.setContent("第一站");
        e1.setOccurredAt(LocalDateTime.of(2026, 7, 1, 9, 0));
        JournalEntry e2 = entry("entry-2", "trip-1", "u-1");
        e2.setContent("第二站");
        e2.setOccurredAt(LocalDateTime.of(2026, 7, 1, 14, 0));
        Page<JournalEntry> page = new Page<>(1, 20);
        page.setRecords(List.of(e1, e2));
        page.setTotal(2);
        when(journalEntryMapper.selectPage(any(Page.class), any(QueryWrapper.class))).thenReturn(page);
        when(journalPhotoMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(
                journalPhoto("entry-1", "file-1", 1),
                journalPhoto("entry-2", "file-2", 1)
        ));
        when(fileMapper.selectBatchIds(anyCollection())).thenReturn(List.of(
                uploadedFile("file-1", "u-1", "image/jpeg", "https://cdn.example.com/e1.jpg"),
                uploadedFile("file-2", "u-1", "image/png", "https://cdn.example.com/e2.jpg")
        ));

        LoginUserContext.setUserId("u-1");
        try {
            var resp = journalEntryService.listEntries("trip-1", 1, 20);

            assertEquals(2, resp.total());
            assertEquals(2, resp.list().size());
            assertEquals("第一站", resp.list().getFirst().content());
            assertEquals("第二站", resp.list().get(1).content());
            assertEquals("https://cdn.example.com/e1.jpg", resp.list().getFirst().photos().getFirst().url());
            assertEquals("https://cdn.example.com/e1.jpg", resp.list().getFirst().photos().getFirst().thumbnailUrl());
            assertEquals("https://cdn.example.com/e2.jpg", resp.list().get(1).photos().getFirst().url());
        } finally {
            LoginUserContext.clear();
        }
    }

    @Test
    void listEntries_returnsPresignedUrlWhenFileHasNoPublicUrl() {
        when(tripMapper.selectById("trip-1")).thenReturn(trip("u-1"));

        JournalEntry entry = entry("entry-1", "trip-1", "u-1");
        entry.setContent("私有桶图片");
        Page<JournalEntry> page = new Page<>(1, 20);
        page.setRecords(List.of(entry));
        page.setTotal(1);
        when(journalEntryMapper.selectPage(any(Page.class), any(QueryWrapper.class))).thenReturn(page);
        when(journalPhotoMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(
                journalPhoto("entry-1", "file-1", 1)
        ));

        File file = uploadedFile("file-1", "u-1", "image/jpeg", null);
        when(fileMapper.selectBatchIds(anyCollection())).thenReturn(List.of(file));
        String presignedUrl = "https://test-bucket.cos.ap-guangzhou.myqcloud.com/private.jpg?sign=xxx";
        when(fileUrlResolver.resolveUrls(file))
                .thenReturn(CiUrlBuilder.CiUrls.mirror(presignedUrl));

        LoginUserContext.setUserId("u-1");
        try {
            var resp = journalEntryService.listEntries("trip-1", 1, 20);

            assertEquals(presignedUrl,
                    resp.list().getFirst().photos().getFirst().url());
        } finally {
            LoginUserContext.clear();
        }
    }

    @Test
    void listEntries_ordersByOccurredAtAsc() {
        when(tripMapper.selectById("trip-1")).thenReturn(trip("u-1"));

        Page<JournalEntry> page = new Page<>(1, 20);
        page.setRecords(List.of());
        page.setTotal(0);
        when(journalEntryMapper.selectPage(any(Page.class), any(QueryWrapper.class))).thenReturn(page);

        LoginUserContext.setUserId("u-1");
        try {
            journalEntryService.listEntries("trip-1", 1, 20);
        } finally {
            LoginUserContext.clear();
        }

        ArgumentCaptor<QueryWrapper> queryCaptor = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(journalEntryMapper).selectPage(any(Page.class), queryCaptor.capture());
        String sql = queryCaptor.getValue().getSqlSegment();
        assertTrue(sql.contains("occurred_at ASC"));
        assertTrue(sql.contains("visibility"));
        assertTrue(sql.contains("user_id"));
    }

    // ---- 5. 编辑日志成功 ----

    @Test
    void updateEntry_updatesFieldsAndReplacesPhotos() {
        when(tripMapper.selectById("trip-1")).thenReturn(trip("u-1"));

        JournalEntry existing = entry("entry-1", "trip-1", "u-1");
        existing.setContent("原始内容");
        existing.setLocationName("原始位置");
        existing.setOccurredAt(LocalDateTime.of(2026, 7, 1, 9, 0));
        existing.setIsHighlight(false);
        when(journalEntryMapper.selectOne(any(QueryWrapper.class))).thenReturn(existing);
        when(journalEntryMapper.updateById(any(JournalEntry.class))).thenReturn(1);
        when(fileMapper.selectBatchIds(anyCollection())).thenReturn(List.of(
                uploadedFile("file-1", "u-1", "image/jpeg", "https://cdn.example.com/new.jpg")
        ));

        LoginUserContext.setUserId("u-1");
        try {
            var resp = journalEntryService.updateEntry("trip-1", "entry-1", new JournalEntryUpdateReq(
                    " 修改后内容 ",
                    location("新位置"),
                    List.of("file-1"),
                    LocalDateTime.of(2026, 7, 1, 10, 0),
                    "relaxed",
                    true,
                    null
            ));

            assertEquals("修改后内容", resp.content());
            assertEquals("新位置", resp.location().name());
            assertEquals(1, resp.photos().size());
            assertEquals("file-1", resp.photos().getFirst().fileId());
            assertEquals("https://cdn.example.com/new.jpg", resp.photos().getFirst().url());
            assertEquals("relaxed", resp.mood());
            assertTrue(resp.isHighlight());
        } finally {
            LoginUserContext.clear();
        }

        verify(journalEntryMapper).updateById(any(JournalEntry.class));
        verify(journalPhotoMapper).softDeleteByJournalEntryId("entry-1", "trip-1", "u-1");
        verify(journalPhotoMapper).insert(any(JournalPhoto.class));
    }

    @Test
    void updateEntry_allowsRemovingAllContentWhenFileIdsPresent() {
        when(tripMapper.selectById("trip-1")).thenReturn(trip("u-1"));

        JournalEntry existing = entry("entry-1", "trip-1", "u-1");
        existing.setContent("旧内容");
        when(journalEntryMapper.selectOne(any(QueryWrapper.class))).thenReturn(existing);
        when(journalEntryMapper.updateById(any(JournalEntry.class))).thenReturn(1);
        when(fileMapper.selectBatchIds(anyCollection())).thenReturn(List.of(
                uploadedFile("file-1", "u-1", "image/jpeg", "https://cdn.example.com/old.jpg")
        ));

        LoginUserContext.setUserId("u-1");
        try {
            var resp = journalEntryService.updateEntry("trip-1", "entry-1", new JournalEntryUpdateReq(
                    "",
                    null,
                    List.of("file-1"),
                    null, null, null, null
            ));

            assertEquals(1, resp.photos().size());
        } finally {
            LoginUserContext.clear();
        }
    }

    // ---- 6. 删除日志成功 ----

    @Test
    void deleteEntry_success() {
        when(tripMapper.selectById("trip-1")).thenReturn(trip("u-1"));

        JournalEntry entryForDelete = entry("entry-1", "trip-1", "u-1");
        when(journalEntryMapper.selectOne(any(QueryWrapper.class))).thenReturn(entryForDelete);
        when(journalEntryMapper.delete(any(UpdateWrapper.class))).thenReturn(1);

        LoginUserContext.setUserId("u-1");
        try {
            journalEntryService.deleteEntry("trip-1", "entry-1");
        } finally {
            LoginUserContext.clear();
        }

        verify(journalEntryMapper).delete(any(UpdateWrapper.class));
        verify(journalPhotoMapper).softDeleteByJournalEntryId("entry-1", "trip-1", "u-1");
    }

    @Test
    void deleteEntry_throwsNotFoundForMissingEntry() {
        when(tripMapper.selectById("trip-1")).thenReturn(trip("u-1"));
        when(journalEntryMapper.selectOne(any(QueryWrapper.class))).thenReturn(null);

        LoginUserContext.setUserId("u-1");
        try {
            assertThrows(NotFoundException.class,
                    () -> journalEntryService.deleteEntry("trip-1", "entry-1"));
            verify(journalPhotoMapper, never()).softDeleteByJournalEntryId(anyString(), anyString(), anyString());
        } finally {
            LoginUserContext.clear();
        }
    }

    // ---- 7. 非本人/无权限访问时失败 ----

    @Test
    void createEntry_rejectsOtherUserTrip() {
        when(tripMapper.selectById("trip-1")).thenReturn(trip("u-2"));

        LoginUserContext.setUserId("u-1");
        try {
            assertThrows(ForbiddenException.class, () -> journalEntryService.createEntry("trip-1",
                    new JournalEntryCreateReq(
                            "测试内容", null,
                            null,
                            LocalDateTime.of(2026, 7, 1, 10, 30),
                            null,
                            false,
                            null
                    )));
            verify(journalEntryMapper, never()).insert(any(JournalEntry.class));
        } finally {
            LoginUserContext.clear();
        }
    }

    @Test
    void listEntries_rejectsOtherUserTrip() {
        when(tripMapper.selectById("trip-1")).thenReturn(trip("u-2"));

        LoginUserContext.setUserId("u-1");
        try {
            assertThrows(ForbiddenException.class,
                    () -> journalEntryService.listEntries("trip-1", 1, 20));
        } finally {
            LoginUserContext.clear();
        }
    }

    @Test
    void updateEntry_rejectsOtherUserTrip() {
        when(tripMapper.selectById("trip-1")).thenReturn(trip("u-2"));

        LoginUserContext.setUserId("u-1");
        try {
            assertThrows(ForbiddenException.class, () -> journalEntryService.updateEntry("trip-1", "entry-1",
                    new JournalEntryUpdateReq(null, null,
                    null, null, null, null, null)));
        } finally {
            LoginUserContext.clear();
        }
    }

    @Test
    void updateEntry_throwsNotFoundForOtherUserEntry() {
        when(tripMapper.selectById("trip-1")).thenReturn(trip("u-1"));
        when(journalEntryMapper.selectOne(any(QueryWrapper.class))).thenReturn(null);

        LoginUserContext.setUserId("u-1");
        try {
            assertThrows(NotFoundException.class, () -> journalEntryService.updateEntry("trip-1", "other-user-entry",
                    new JournalEntryUpdateReq("修改", null,
                    null, null, null, null, null)));
            verify(journalEntryMapper, never()).updateById(any(JournalEntry.class));
        } finally {
            LoginUserContext.clear();
        }
    }

    @Test
    void updateEntry_rejectsOtherUsersEntryEvenForTripOwner() {
        TripMember owner = new TripMember();
        owner.setRole("owner");
        doReturn(new TripAccessService.TripAccess(trip("u-1"), owner))
                .when(tripAccessService)
                .requirePermission("trip-1", "u-1", TripPermission.EDIT_CONTENT);
        when(journalEntryMapper.selectOne(any(QueryWrapper.class)))
                .thenReturn(entry("entry-2", "trip-1", "u-2"));

        LoginUserContext.setUserId("u-1");
        try {
            assertThrows(ForbiddenException.class, () -> journalEntryService.updateEntry(
                    "trip-1",
                    "entry-2",
                    new JournalEntryUpdateReq("越权修改", null, null, null, null, null, null)));
            verify(journalEntryMapper, never()).updateById(any(JournalEntry.class));
        } finally {
            LoginUserContext.clear();
        }
    }

    @Test
    void deleteEntry_rejectsOtherUserTrip() {
        when(tripMapper.selectById("trip-1")).thenReturn(trip("u-2"));

        LoginUserContext.setUserId("u-1");
        try {
            assertThrows(ForbiddenException.class,
                    () -> journalEntryService.deleteEntry("trip-1", "entry-1"));
            verify(journalEntryMapper, never()).delete(any(UpdateWrapper.class));
            verify(journalPhotoMapper, never()).softDeleteByJournalEntryId(anyString(), anyString(), anyString());
        } finally {
            LoginUserContext.clear();
        }
    }

    @Test
    void deleteEntry_throwsNotFoundForOtherUserEntry() {
        when(tripMapper.selectById("trip-1")).thenReturn(trip("u-1"));
        when(journalEntryMapper.selectOne(any(QueryWrapper.class))).thenReturn(null);

        LoginUserContext.setUserId("u-1");
        try {
            assertThrows(NotFoundException.class,
                    () -> journalEntryService.deleteEntry("trip-1", "other-user-entry"));
        } finally {
            LoginUserContext.clear();
        }
    }

    @Test
    void featurePhoto_rejectsPrivateJournalPhoto() {
        when(tripMapper.selectById("trip-1")).thenReturn(trip("u-1"));
        JournalPhoto photo = journalPhoto("entry-private", "file-1", 1);
        photo.setId("photo-private");
        when(journalPhotoMapper.selectOne(any(QueryWrapper.class))).thenReturn(photo);
        JournalEntry privateEntry = entry("entry-private", "trip-1", "u-1");
        privateEntry.setVisibility("private");
        when(journalEntryMapper.selectOne(any(QueryWrapper.class))).thenReturn(privateEntry);

        LoginUserContext.setUserId("u-1");
        try {
            assertThrows(BizException.class,
                    () -> journalEntryService.featurePhoto("trip-1", "photo-private"));
            verify(journalPhotoMapper, never()).updateFeatured(anyString(), anyString(), anyBoolean());
        } finally {
            LoginUserContext.clear();
        }
    }

    @Test
    void updateEntry_makingEntryPrivateClearsFeaturedPhotos() {
        when(tripMapper.selectById("trip-1")).thenReturn(trip("u-1"));
        JournalEntry existing = entry("entry-1", "trip-1", "u-1");
        existing.setContent("公开日志");
        JournalPhoto featured = journalPhoto("entry-1", "file-1", 1);
        featured.setIsFeatured(true);
        when(journalEntryMapper.selectOne(any(QueryWrapper.class))).thenReturn(existing);
        when(journalEntryMapper.updateById(any(JournalEntry.class))).thenReturn(1);
        when(journalPhotoMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(featured));

        LoginUserContext.setUserId("u-1");
        try {
            JournalEntryResp response = journalEntryService.updateEntry(
                    "trip-1",
                    "entry-1",
                    new JournalEntryUpdateReq(null, null, null, null, null, null, "private"));

            assertEquals("private", response.visibility());
            assertFalse(response.photos().getFirst().userFeatured());
            verify(journalPhotoMapper).clearFeaturedByJournalEntryId("entry-1", "trip-1");
        } finally {
            LoginUserContext.clear();
        }
    }

    // ---- 已结束旅程：日志内容锁定（日期相对 DateTimeUtil.todayBusiness()，避免用例过期） ----

    @Test
    void createEntry_completedTrip_rejects() {
        LocalDate today = DateTimeUtil.todayBusiness();
        when(tripMapper.selectById("trip-1"))
                .thenReturn(tripWithDates("u-1", today.minusDays(10), today.minusDays(5)));

        LoginUserContext.setUserId("u-1");
        try {
            BizException ex = assertThrows(BizException.class, () -> journalEntryService.createEntry(
                    "trip-1",
                    new JournalEntryCreateReq(
                            "已经结束的旅程不该能补日志", null, null, LocalDateTime.now(), null, null, null)));

            assertEquals(ErrorCode.TRIP_COMPLETED_CONTENT_LOCKED, ex.getErrorCode());
            verify(journalEntryMapper, never()).insert(any(JournalEntry.class));
        } finally {
            LoginUserContext.clear();
        }
    }

    // ---- helpers ----

    /**
     * 进行中的行程：日期相对 today 构造，避免用例随真实日期过期
     * （写死日期会命中 TRIP_COMPLETED_CONTENT_LOCKED，见 ItineraryServiceImplTest 的同一约定）。
     */
    private Trip trip(String userId) {
        LocalDate today = DateTimeUtil.todayBusiness();
        return tripWithDates(userId, today.minusDays(1), today.plusDays(3));
    }

    private Trip tripWithDates(String userId, LocalDate startDate, LocalDate endDate) {
        Trip trip = new Trip();
        trip.setId("trip-1");
        trip.setUserId(userId);
        trip.setTitle("东京 5 日");
        trip.setStartDate(startDate);
        trip.setEndDate(endDate);
        trip.setBaseCurrency("CNY");
        return trip;
    }

    private JournalEntry entry(String id, String tripId, String userId) {
        JournalEntry entry = new JournalEntry();
        entry.setId(id);
        entry.setTripId(tripId);
        entry.setUserId(userId);
        entry.setOccurredAt(LocalDateTime.of(2026, 7, 1, 10, 30));
        entry.setCreatedAt(LocalDateTime.of(2026, 7, 1, 10, 31));
        entry.setIsHighlight(false);
        entry.setVisibility("all");
        entry.setSortOrder(0);
        return entry;
    }

    private JournalEntryLocationReq location(String name) {
        return new JournalEntryLocationReq(
                "poi-" + name,
                name,
                name + "地址",
                new BigDecimal("35.0000000"),
                new BigDecimal("139.0000000"),
                "130000",
                "03",
                "东京都",
                "东京都",
                null,
                "MANUAL",
                null,
                "日本"
        );
    }

    private JournalPhoto journalPhoto(String entryId, String fileId, int sortOrder) {
        JournalPhoto photo = new JournalPhoto();
        photo.setJournalEntryId(entryId);
        photo.setTripId("trip-1");
        photo.setUserId("u-1");
        photo.setFileId(fileId);
        photo.setSortOrder(sortOrder);
        return photo;
    }

    private File uploadedFile(String id, String userId, String contentType, String url) {
        File file = new File();
        file.setId(id);
        file.setUserId(userId);
        file.setContentType(contentType);
        file.setStatus("UPLOADED");
        file.setUrl(url);
        file.setObjectKey("users/" + userId + "/journal/2026/07/" + id + ".jpg");
        file.setBucket("test-bucket");
        file.setRegion("ap-guangzhou");
        file.setSize(100000L);
        file.setWidth(1080);
        file.setHeight(1440);
        file.setOriginalFileName("photo.jpg");
        file.setBusinessType("JOURNAL_PHOTO");
        file.setBusinessId("trip-1");
        file.setCreatedAt(LocalDateTime.of(2026, 7, 1, 10, 0));
        return file;
    }

    private File pendingFile(String id, String userId, String contentType) {
        File file = new File();
        file.setId(id);
        file.setUserId(userId);
        file.setContentType(contentType);
        file.setStatus("PENDING");
        file.setSize(100000L);
        file.setOriginalFileName("photo.jpg");
        file.setBusinessType("JOURNAL_PHOTO");
        file.setBusinessId("trip-1");
        return file;
    }
}
