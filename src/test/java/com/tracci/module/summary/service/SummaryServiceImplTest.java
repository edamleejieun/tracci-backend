package com.tracci.module.summary.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tracci.common.context.LoginUserContext;
import com.tracci.common.exception.ForbiddenException;
import com.tracci.integration.cos.CiUrlBuilder;
import com.tracci.integration.cos.FileUrlResolver;
import com.tracci.module.expense.repository.ExpenseMapper;
import com.tracci.module.file.entity.File;
import com.tracci.module.file.repository.FileMapper;
import com.tracci.module.journal.entity.JournalEntry;
import com.tracci.module.journal.entity.JournalPhoto;
import com.tracci.module.journal.repository.JournalEntryMapper;
import com.tracci.module.journal.repository.JournalPhotoMapper;
import com.tracci.module.trip.entity.ItineraryNode;
import com.tracci.module.trip.entity.Trip;
import com.tracci.module.trip.entity.TripDay;
import com.tracci.module.trip.repository.ItineraryNodeMapper;
import com.tracci.module.trip.repository.TripDayMapper;
import com.tracci.module.trip.repository.TripMapper;
import com.tracci.module.trip.support.TripDestinationCoverProvider;
import com.tracci.module.trip.service.TripAccessService;
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
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static com.tracci.testsupport.TripAccessTestSupport.stubOwnerAccess;

@ExtendWith(MockitoExtension.class)
class SummaryServiceImplTest {

    @Mock
    private TripMapper tripMapper;
    @Mock
    private TripDayMapper tripDayMapper;
    @Mock
    private ItineraryNodeMapper itineraryNodeMapper;
    @Mock
    private ExpenseMapper expenseMapper;
    @Mock
    private JournalEntryMapper journalEntryMapper;
    @Mock
    private JournalPhotoMapper journalPhotoMapper;
    @Mock
    private FileMapper fileMapper;
    @Mock
    private FileUrlResolver fileUrlResolver;
    @Mock
    private CiUrlBuilder ciUrlBuilder;
    @Mock
    private TripDestinationCoverProvider destinationCoverProvider;
    @Mock
    private TripAccessService tripAccessService;

    @InjectMocks
    private SummaryServiceImpl summaryService;

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

    @Test
    void getSummary_returnsAggregatedTripSummary() throws JsonProcessingException {
        Trip trip = trip("u-1");
        trip.setCoverFileId("cover-file-1");
        when(tripMapper.selectById("trip-1")).thenReturn(trip);
        when(tripDayMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(
                day("day-1", 1, LocalDate.of(2026, 5, 15)),
                day("day-2", 2, LocalDate.of(2026, 5, 16))
        ));
        when(itineraryNodeMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(
                node("node-1", "day-1", "清水寺", "京都", LocalTime.of(9, 0), LocalTime.of(11, 0)),
                node("node-2", "day-2", "伏见稻荷", "京都", LocalTime.of(10, 0), LocalTime.of(12, 0))
        ));
        // occurredAt is stored as UTC wall-clock. Use times that map to the intended
        // Asia/Shanghai business day (e.g. 10:30 UTC = 18:30 CST same calendar date).
        when(journalEntryMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(
                entry("log-3", "最近普通日志", "京都站", LocalDateTime.of(2026, 5, 16, 12, 0), false),
                entry("log-2", "精选二", "伏见稻荷", LocalDateTime.of(2026, 5, 16, 10, 0), true),
                entry("log-1", "精选一", "清水寺", LocalDateTime.of(2026, 5, 15, 10, 30), true)
        ));
        when(journalPhotoMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(
                photo("log-1", "file-1", 1),
                featuredPhoto("log-2", "file-2", 1),
                photo("log-3", "file-3", 1),
                photo("log-3", "file-pending", 2),
                photo("log-3", "file-deleted", 3)
        ));
        when(fileMapper.selectBatchIds(anyCollection())).thenReturn(List.of(
                uploadedFile("file-1", "https://cdn.example.com/1.jpg"),
                uploadedFile("file-2", "https://cdn.example.com/2.jpg"),
                uploadedFile("file-3", "https://cdn.example.com/3.jpg"),
                fileWithStatus("file-pending", "PENDING"),
                fileWithStatus("file-deleted", "DELETED")
        ));
        when(fileMapper.selectById("cover-file-1")).thenReturn(coverFile("cover-file-1"));
        when(expenseMapper.sumVisibleAmountCnyByTripIdAndUserId("trip-1", "u-1")).thenReturn(new BigDecimal("5860.50"));
        when(expenseMapper.categoryVisibleByTripIdAndUserId("trip-1", "u-1")).thenReturn(List.of(
                new ExpenseMapper.CategorySummary("food", new BigDecimal("1520.00"), 8),
                new ExpenseMapper.CategorySummary("transport", new BigDecimal("980.50"), 4)
        ));

        LoginUserContext.setUserId("u-1");
        try {
            var resp = summaryService.getSummary("trip-1");

            assertEquals("trip-1", resp.trip().id());
            assertEquals("京都五日旅行", resp.trip().title());
            assertEquals("京都五日旅行", resp.trip().destination());
            assertEquals("completed", resp.trip().status());
            assertEquals("cover-file-1", resp.trip().coverPhoto().fileId());
            assertEquals("https://cdn.example.com/cover.jpg", resp.trip().coverPhoto().url());
            assertEquals(1200, resp.trip().coverPhoto().width());
            assertEquals(800, resp.trip().coverPhoto().height());
            assertEquals(2, resp.stats().dayCount());
            assertEquals(2, resp.stats().itineraryCount());
            assertEquals(3, resp.stats().journalCount());
            assertEquals(3, resp.stats().photoCount());
            assertEquals(12, resp.stats().expenseCount());
            assertEquals(2, resp.stats().highlightCount());
            assertEquals("5860.50", resp.stats().totalExpenseAmount());
            assertEquals("CNY", resp.stats().currency());

            assertEquals("5860.50", resp.expenseSummary().totalAmount());
            assertEquals(2, resp.expenseSummary().categoryStats().size());
            assertEquals("food", resp.expenseSummary().categoryStats().getFirst().category());
            assertEquals("餐饮", resp.expenseSummary().categoryStats().getFirst().categoryName());
            assertEquals("1520.00", resp.expenseSummary().categoryStats().getFirst().amount());
            assertEquals("25.94", resp.expenseSummary().categoryStats().getFirst().percentage());
            assertEquals(8, resp.expenseSummary().categoryStats().getFirst().count());

            assertEquals(2, resp.highlightLogs().size());
            assertEquals("log-2", resp.highlightLogs().getFirst().id());
            assertTrue(resp.highlightLogs().getFirst().isHighlight());
            assertEquals(1, resp.highlightLogs().getFirst().photos().size());

            assertEquals(1, resp.userFeaturedPhotos().size());
            assertEquals("file-2", resp.userFeaturedPhotos().getFirst().fileId());
            assertTrue(resp.userFeaturedPhotos().getFirst().userFeatured());
            assertEquals(2, resp.recommendedPhotos().size());
            assertEquals("file-1", resp.recommendedPhotos().getFirst().fileId());
            assertEquals("file-3", resp.recommendedPhotos().get(1).fileId());
            assertFalse(resp.recommendedPhotos().stream().anyMatch(photo -> "file-pending".equals(photo.fileId())));
            assertFalse(resp.recommendedPhotos().stream().anyMatch(photo -> "file-deleted".equals(photo.fileId())));

            assertEquals(2, resp.dailyTimeline().size());
            assertEquals("2026-05-15", resp.dailyTimeline().getFirst().date());
            assertEquals(1, resp.dailyTimeline().getFirst().dayIndex());
            assertEquals(1, resp.dailyTimeline().getFirst().itineraries().size());
            assertEquals("清水寺", resp.dailyTimeline().getFirst().itineraries().getFirst().title());
            assertEquals(1, resp.dailyTimeline().getFirst().logs().size());
            assertEquals("log-1", resp.dailyTimeline().getFirst().logs().getFirst().id());

            String json = new ObjectMapper().writeValueAsString(resp);
            assertFalse(json.contains("imageUrls"));
            assertFalse(json.contains("featuredPhotos"));
            assertTrue(json.contains("userFeaturedPhotos"));
            assertTrue(json.contains("recommendedPhotos"));

            ArgumentCaptor<QueryWrapper<JournalEntry>> journalQueryCaptor = ArgumentCaptor.forClass(QueryWrapper.class);
            verify(journalEntryMapper).selectList(journalQueryCaptor.capture());
            String journalSql = journalQueryCaptor.getValue().getSqlSegment();
            assertTrue(journalSql.contains("visibility"));
            assertTrue(journalSql.contains("user_id"));
        } finally {
            LoginUserContext.clear();
        }
    }

    @Test
    void getSummary_groupsJournalByAsiaShanghaiBusinessDayAcrossUtcBoundary() {
        // iOS writes trip-day midnight as 16:00Z previous UTC calendar day.
        when(tripMapper.selectById("trip-1")).thenReturn(trip("u-1"));
        when(tripDayMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(
                day("day-1", 1, LocalDate.of(2026, 5, 15)),
                day("day-2", 2, LocalDate.of(2026, 5, 16))
        ));
        when(itineraryNodeMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of());
        when(journalEntryMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(
                entry("log-day1-midnight", "第一天凌晨", "京都", LocalDateTime.of(2026, 5, 14, 16, 0), false),
                entry("log-day1-morning", "第一天早上", "京都", LocalDateTime.of(2026, 5, 14, 23, 30), false),
                entry("log-day2", "第二天", "京都", LocalDateTime.of(2026, 5, 15, 16, 0), true)
        ));
        when(journalPhotoMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(
                photo("log-day1-midnight", "file-a", 1),
                photo("log-day1-morning", "file-b", 1),
                photo("log-day2", "file-c", 1)
        ));
        when(fileMapper.selectBatchIds(anyCollection())).thenReturn(List.of(
                uploadedFile("file-a", "https://cdn.example.com/a.jpg"),
                uploadedFile("file-b", "https://cdn.example.com/b.jpg"),
                uploadedFile("file-c", "https://cdn.example.com/c.jpg")
        ));
        when(expenseMapper.sumVisibleAmountCnyByTripIdAndUserId("trip-1", "u-1")).thenReturn(BigDecimal.ZERO);
        when(expenseMapper.categoryVisibleByTripIdAndUserId("trip-1", "u-1")).thenReturn(List.of());

        LoginUserContext.setUserId("u-1");
        try {
            var resp = summaryService.getSummary("trip-1");

            assertEquals(2, resp.dailyTimeline().size());
            var day1 = resp.dailyTimeline().getFirst();
            var day2 = resp.dailyTimeline().get(1);
            assertEquals("2026-05-15", day1.date());
            assertEquals(2, day1.logs().size());
            assertEquals(2, day1.logs().stream().mapToLong(log -> log.photos().size()).sum());
            assertEquals("2026-05-16", day2.date());
            assertEquals(1, day2.logs().size());
            assertEquals("log-day2", day2.logs().getFirst().id());
            assertEquals(1, day2.logs().getFirst().photos().size());
        } finally {
            LoginUserContext.clear();
        }
    }

    @Test
    void getSummary_rejectsOtherUserTrip() {
        when(tripMapper.selectById("trip-1")).thenReturn(trip("u-2"));

        LoginUserContext.setUserId("u-1");
        try {
            assertThrows(ForbiddenException.class, () -> summaryService.getSummary("trip-1"));
            verify(journalEntryMapper, never()).selectList(any(QueryWrapper.class));
        } finally {
            LoginUserContext.clear();
        }
    }

    @Test
    void getSummary_returnsEmptyStructureForTripWithoutData() {
        when(tripMapper.selectById("trip-1")).thenReturn(trip("u-1"));
        when(tripDayMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(
                day("day-1", 1, LocalDate.of(2026, 5, 15)),
                day("day-2", 2, LocalDate.of(2026, 5, 16))
        ));
        when(itineraryNodeMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of());
        when(journalEntryMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of());
        when(expenseMapper.sumVisibleAmountCnyByTripIdAndUserId("trip-1", "u-1")).thenReturn(BigDecimal.ZERO);
        when(expenseMapper.categoryVisibleByTripIdAndUserId("trip-1", "u-1")).thenReturn(List.of());

        LoginUserContext.setUserId("u-1");
        try {
            var resp = summaryService.getSummary("trip-1");

            assertEquals(2, resp.stats().dayCount());
            assertEquals(0, resp.stats().itineraryCount());
            assertEquals(0, resp.stats().journalCount());
            assertEquals(0, resp.stats().photoCount());
            assertEquals(0, resp.stats().expenseCount());
            assertEquals("0.00", resp.stats().totalExpenseAmount());
            assertTrue(resp.expenseSummary().categoryStats().isEmpty());
            assertTrue(resp.highlightLogs().isEmpty());
            assertTrue(resp.recommendedPhotos().isEmpty());
            assertEquals(2, resp.dailyTimeline().size());
            assertTrue(resp.dailyTimeline().getFirst().itineraries().isEmpty());
            assertTrue(resp.dailyTimeline().getFirst().logs().isEmpty());
            verify(journalPhotoMapper, never()).selectList(any(QueryWrapper.class));
            verify(fileMapper, never()).selectBatchIds(anyCollection());
        } finally {
            LoginUserContext.clear();
        }
    }

    private Trip trip(String userId) {
        Trip trip = new Trip();
        trip.setId("trip-1");
        trip.setUserId(userId);
        trip.setTitle("京都五日旅行");
        trip.setStartDate(LocalDate.of(2026, 5, 15));
        trip.setEndDate(LocalDate.of(2026, 5, 16));
        trip.setBaseCurrency("CNY");
        trip.setStatus("planning");
        return trip;
    }

    private TripDay day(String id, int dayIndex, LocalDate date) {
        TripDay day = new TripDay();
        day.setId(id);
        day.setTripId("trip-1");
        day.setUserId("u-1");
        day.setDayIndex(dayIndex);
        day.setDate(date);
        return day;
    }

    private ItineraryNode node(
            String id,
            String dayId,
            String title,
            String address,
            LocalTime startTime,
            LocalTime endTime) {
        ItineraryNode node = new ItineraryNode();
        node.setId(id);
        node.setTripId("trip-1");
        node.setTripDayId(dayId);
        node.setUserId("u-1");
        node.setTitle(title);
        node.setAddress(address);
        node.setStartTime(startTime);
        node.setEndTime(endTime);
        return node;
    }

    private JournalEntry entry(
            String id,
            String content,
            String locationName,
            LocalDateTime occurredAt,
            boolean highlight) {
        JournalEntry entry = new JournalEntry();
        entry.setId(id);
        entry.setTripId("trip-1");
        entry.setUserId("u-1");
        entry.setContent(content);
        entry.setLocationName(locationName);
        entry.setOccurredAt(occurredAt);
        entry.setCreatedAt(occurredAt.plusMinutes(1));
        entry.setIsHighlight(highlight);
        entry.setSortOrder(0);
        return entry;
    }

    private JournalPhoto photo(String entryId, String fileId, int sortOrder) {
        JournalPhoto photo = new JournalPhoto();
        photo.setJournalEntryId(entryId);
        photo.setTripId("trip-1");
        photo.setUserId("u-1");
        photo.setFileId(fileId);
        photo.setSortOrder(sortOrder);
        return photo;
    }

    private JournalPhoto featuredPhoto(String entryId, String fileId, int sortOrder) {
        JournalPhoto photo = photo(entryId, fileId, sortOrder);
        photo.setIsFeatured(true);
        return photo;
    }

    private File uploadedFile(String id, String url) {
        File file = fileWithStatus(id, "UPLOADED");
        file.setUrl(url);
        return file;
    }

    private File coverFile(String id) {
        File file = uploadedFile(id, "https://cdn.example.com/cover.jpg");
        file.setBusinessType("TRIP_COVER");
        file.setWidth(1200);
        file.setHeight(800);
        return file;
    }

    private File fileWithStatus(String id, String status) {
        File file = new File();
        file.setId(id);
        file.setUserId("u-1");
        file.setContentType("image/jpeg");
        file.setStatus(status);
        file.setObjectKey("users/u-1/journal/" + id + ".jpg");
        file.setWidth(1080);
        file.setHeight(1440);
        return file;
    }
}
