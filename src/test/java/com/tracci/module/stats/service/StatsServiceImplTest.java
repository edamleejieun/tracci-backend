package com.tracci.module.stats.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tracci.common.context.LoginUserContext;
import com.tracci.common.exception.AuthException;
import com.tracci.common.exception.ForbiddenException;
import com.tracci.module.expense.repository.ExpenseMapper;
import com.tracci.module.journal.repository.JournalEntryMapper;
import com.tracci.module.journal.repository.JournalPhotoMapper;
import com.tracci.module.summary.dto.response.SummaryResp;
import com.tracci.module.summary.service.SummaryService;
import com.tracci.module.trip.entity.Trip;
import com.tracci.module.trip.repository.ItineraryNodeMapper;
import com.tracci.module.trip.repository.TripMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StatsServiceImplTest {

    @Mock
    private TripMapper tripMapper;
    @Mock
    private ItineraryNodeMapper itineraryNodeMapper;
    @Mock
    private JournalEntryMapper journalEntryMapper;
    @Mock
    private JournalPhotoMapper journalPhotoMapper;
    @Mock
    private ExpenseMapper expenseMapper;
    @Mock
    private SummaryService summaryService;

    @InjectMocks
    private StatsServiceImpl statsService;

    @AfterEach
    void tearDown() {
        LoginUserContext.clear();
    }

    @Test
    void getOverview_global_returnsAggregatedStats() {
        LoginUserContext.setUserId("u-1");
        LocalDate today = LocalDate.now();
        when(tripMapper.selectList(any())).thenReturn(List.of(
                trip("t-1", "京都五日旅行", today.minusDays(9), today.minusDays(5)),
                trip("t-2", "东京三日旅行", today.minusDays(4), today.minusDays(2))
        ));
        List<String> tripIds = List.of("t-1", "t-2");
        when(itineraryNodeMapper.countByUserIdAndTripIds("u-1", tripIds)).thenReturn(20L);
        when(journalEntryMapper.countVisibleByTripIdsAndUserId(tripIds, "u-1")).thenReturn(15L);
        when(journalPhotoMapper.countVisibleUploadedPhotoByTripIdsAndUserId(tripIds, "u-1")).thenReturn(45L);
        when(expenseMapper.categoryVisibleByTripIdsAndUserId(tripIds, "u-1")).thenReturn(List.of(
                new ExpenseMapper.CategorySummary("food", new BigDecimal("3000.00"), 10),
                new ExpenseMapper.CategorySummary("transport", new BigDecimal("2000.00"), 5)
        ));
        when(expenseMapper.sumVisibleAmountCnyByTripIdsAndUserId(tripIds, "u-1")).thenReturn(List.of(
                new ExpenseMapper.TripAmountSummary("t-1", new BigDecimal("5000.00")),
                new ExpenseMapper.TripAmountSummary("t-2", new BigDecimal("3560.00"))
        ));
        YearMonth currentMonth = YearMonth.now();
        when(expenseMapper.monthlyVisibleByTripIdsAndUserId(eq(tripIds), eq("u-1"), any(), any()))
                .thenReturn(List.of(
                        new ExpenseMapper.MonthlyAmountSummary(currentMonth.minusMonths(1).toString(), new BigDecimal("1200.00"), 3),
                        new ExpenseMapper.MonthlyAmountSummary(currentMonth.toString(), new BigDecimal("860.00"), 2)
                ));

        var resp = statsService.getOverview(null);

        assertEquals("ALL", resp.scope().type());
        assertEquals(2, resp.overview().tripCount());
        assertEquals(8, resp.overview().totalTravelDays());
        assertEquals(2, resp.overview().destinationCount());
        assertEquals(20, resp.overview().itineraryCount());
        assertEquals(15, resp.overview().journalCount());
        assertEquals(45, resp.overview().photoCount());
        assertEquals(15, resp.overview().expenseCount());
        assertEquals("8560.00", resp.overview().totalExpenseAmount());
        assertEquals("1070.00", resp.overview().averageDailyExpense());
        assertEquals("CNY", resp.overview().currency());

        assertEquals("8560.00", resp.expenseStats().totalAmount());
        assertEquals(2, resp.expenseStats().categoryStats().size());
        assertEquals("food", resp.expenseStats().categoryStats().getFirst().category());
        assertEquals("餐饮", resp.expenseStats().categoryStats().getFirst().categoryName());
        assertEquals("3000.00", resp.expenseStats().categoryStats().getFirst().amount());
        assertEquals("35.05", resp.expenseStats().categoryStats().getFirst().percentage());
        assertEquals(10, resp.expenseStats().categoryStats().getFirst().count());

        assertEquals(2, resp.recentTrips().size());
        assertEquals("t-1", resp.recentTrips().getFirst().id());
        assertEquals("京都五日旅行", resp.recentTrips().getFirst().title());
        assertEquals("completed", resp.recentTrips().getFirst().status());
        assertEquals("5000.00", resp.recentTrips().getFirst().totalExpenseAmount());
        assertNotNull(resp.insights());
        assertEquals(3, resp.analysisEntries().size());
        assertEquals("spend", resp.analysisEntries().getFirst().kind());
        assertNotNull(resp.memory());

        assertEquals(5, resp.monthlyTrend().size());
        assertEquals(currentMonth.minusMonths(4).toString(), resp.monthlyTrend().getFirst().yearMonth());
        assertEquals("0.00", resp.monthlyTrend().getFirst().amount());
        assertEquals(currentMonth.minusMonths(1).toString(), resp.monthlyTrend().get(3).yearMonth());
        assertEquals("1200.00", resp.monthlyTrend().get(3).amount());
        assertEquals(3, resp.monthlyTrend().get(3).count());
        assertEquals(currentMonth.toString(), resp.monthlyTrend().get(4).yearMonth());
        assertEquals("860.00", resp.monthlyTrend().get(4).amount());
        assertTrue(resp.dailyTrend().isEmpty());
        verify(expenseMapper, never()).sumVisibleAmountCnyByTripIdAndUserId(any(), any());
    }

    @Test
    void getOverview_singleTrip_delegatesToSummaryService() {
        LoginUserContext.setUserId("u-1");

        SummaryResp summary = createSummaryResp();
        when(summaryService.getSummary("trip-1")).thenReturn(summary);
        when(expenseMapper.dailyVisibleByTripIdAndUserId("trip-1", "u-1")).thenReturn(List.of(
                new ExpenseMapper.DailyAmountSummary(LocalDate.of(2026, 7, 1), new BigDecimal("1200.00")),
                new ExpenseMapper.DailyAmountSummary(LocalDate.of(2026, 7, 3), new BigDecimal("360.50"))
        ));

        var resp = statsService.getOverview("trip-1");

        assertEquals("TRIP", resp.scope().type());
        assertEquals("trip-1", resp.scope().tripId());
        assertEquals("京都五日旅行", resp.scope().tripTitle());

        assertEquals(1, resp.overview().tripCount());
        assertEquals(5, resp.overview().totalTravelDays());
        assertEquals(12, resp.overview().itineraryCount());
        assertEquals(8, resp.overview().journalCount());
        assertEquals(24, resp.overview().photoCount());
        assertEquals(1, resp.overview().destinationCount());
        assertEquals(34, resp.overview().expenseCount());
        assertEquals("8560.00", resp.overview().totalExpenseAmount());
        assertEquals("1712.00", resp.overview().averageDailyExpense());
        assertEquals("CNY", resp.overview().currency());

        assertEquals("8560.00", resp.expenseStats().totalAmount());
        assertEquals("food", resp.expenseStats().categoryStats().getFirst().category());
        assertEquals("餐饮", resp.expenseStats().categoryStats().getFirst().categoryName());

        assertTrue(resp.recentTrips().isEmpty());
        assertTrue(resp.monthlyTrend().isEmpty());
        assertEquals(2, resp.dailyTrend().size());
        assertEquals("到达日", resp.dailyTrend().getFirst().highlight());
        assertEquals("1200.00", resp.dailyTrend().getFirst().expenseAmount());
        assertEquals(2, resp.dailyTrend().getFirst().photoCount());
        assertEquals("360.50", resp.dailyTrend().get(1).expenseAmount());
        assertNull(resp.dailyTrend().get(1).journalCount());
        assertNotNull(resp.memory());

        verify(tripMapper, never()).selectList(any());
        verify(itineraryNodeMapper, never()).countByUserId(any());
    }

    @Test
    void getOverview_singleTrip_forbiddenForOtherUser() {
        LoginUserContext.setUserId("u-1");
        when(summaryService.getSummary("trip-other")).thenThrow(new ForbiddenException());

        assertThrows(ForbiddenException.class, () -> statsService.getOverview("trip-other"));
        verify(tripMapper, never()).selectList(any());
    }

    @Test
    void getOverview_global_requiresLogin() {
        assertThrows(AuthException.class, () -> statsService.getOverview(null));
    }

    @Test
    void getOverview_singleTrip_authDelegatedToSummaryService() {
        LoginUserContext.setUserId("u-1");
        when(summaryService.getSummary("trip-1")).thenThrow(new AuthException(
                com.tracci.common.response.ErrorCode.TOKEN_MISSING));

        assertThrows(AuthException.class, () -> statsService.getOverview("trip-1"));
    }

    @Test
    void getOverview_global_emptyDataForNewUser() {
        LoginUserContext.setUserId("u-new");
        when(tripMapper.selectList(any())).thenReturn(List.of());

        var resp = statsService.getOverview(null);

        assertEquals("ALL", resp.scope().type());
        assertEquals(0, resp.overview().tripCount());
        assertEquals(0, resp.overview().totalTravelDays());
        assertEquals(0, resp.overview().destinationCount());
        assertEquals(0, resp.overview().itineraryCount());
        assertEquals(0, resp.overview().journalCount());
        assertEquals(0, resp.overview().photoCount());
        assertEquals(0, resp.overview().expenseCount());
        assertEquals("0.00", resp.overview().totalExpenseAmount());
        assertEquals("0.00", resp.overview().averageDailyExpense());
        assertTrue(resp.expenseStats().categoryStats().isEmpty());
        assertTrue(resp.recentTrips().isEmpty());
        verify(itineraryNodeMapper, never()).countByUserIdAndTripIds(eq("u-new"), any());
        verify(journalEntryMapper, never()).countVisibleByTripIdsAndUserId(any(), eq("u-new"));
        verify(journalPhotoMapper, never()).countVisibleUploadedPhotoByTripIdsAndUserId(any(), eq("u-new"));
        verify(expenseMapper, never()).sumAmountCnyByUserIdAndTripIds(eq("u-new"), any());
    }

    @Test
    void getOverview_global_usesRequestedTimeZoneForStartedTripBoundary() {
        LoginUserContext.setUserId("u-1");
        LocalDate expectedToday = LocalDate.now(ZoneId.of("Pacific/Kiritimati"));
        when(tripMapper.listStartedMemberTrips("u-1", expectedToday)).thenReturn(List.of());
        when(tripMapper.selectList(any())).thenReturn(List.of());

        statsService.getOverview(null, "Pacific/Kiritimati");

        verify(tripMapper).listStartedMemberTrips("u-1", expectedToday);
    }

    @Test
    void getOverview_global_filtersNotStartedTrips() {
        LoginUserContext.setUserId("u-1");
        LocalDate today = LocalDate.now();
        Trip startedTrip = trip("t-started", "已出发", today.minusDays(2), today);
        Trip futureTrip = trip("t-future", "规划中", today.plusDays(10), today.plusDays(12));
        when(tripMapper.selectList(any())).thenReturn(List.of(futureTrip, startedTrip));
        List<String> tripIds = List.of("t-started");
        when(itineraryNodeMapper.countByUserIdAndTripIds("u-1", tripIds)).thenReturn(1L);
        when(journalEntryMapper.countVisibleByTripIdsAndUserId(tripIds, "u-1")).thenReturn(1L);
        when(journalPhotoMapper.countVisibleUploadedPhotoByTripIdsAndUserId(tripIds, "u-1")).thenReturn(1L);
        when(expenseMapper.categoryVisibleByTripIdsAndUserId(tripIds, "u-1")).thenReturn(List.of(
                new ExpenseMapper.CategorySummary("food", new BigDecimal("100.00"), 1)
        ));
        when(expenseMapper.sumVisibleAmountCnyByTripIdsAndUserId(tripIds, "u-1")).thenReturn(List.of(
                new ExpenseMapper.TripAmountSummary("t-started", new BigDecimal("100.00"))
        ));
        when(expenseMapper.monthlyVisibleByTripIdsAndUserId(eq(tripIds), eq("u-1"), any(), any()))
                .thenReturn(List.of());

        var resp = statsService.getOverview(null);

        assertEquals(1, resp.overview().tripCount());
        assertEquals("t-started", resp.recentTrips().getFirst().id());
    }

    @Test
    void getOverview_global_dataIsolationBetweenUsers() {
        LoginUserContext.setUserId("u-1");
        when(tripMapper.selectList(any())).thenReturn(List.of(
                trip("t-1", "trip a", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 2))
        ));
        List<String> tripIds = List.of("t-1");
        when(itineraryNodeMapper.countByUserIdAndTripIds("u-1", tripIds)).thenReturn(5L);
        when(journalEntryMapper.countVisibleByTripIdsAndUserId(tripIds, "u-1")).thenReturn(3L);
        when(journalPhotoMapper.countVisibleUploadedPhotoByTripIdsAndUserId(tripIds, "u-1")).thenReturn(10L);
        when(expenseMapper.categoryVisibleByTripIdsAndUserId(tripIds, "u-1")).thenReturn(List.of(
                new ExpenseMapper.CategorySummary("food", new BigDecimal("100.00"), 2)
        ));
        when(expenseMapper.sumVisibleAmountCnyByTripIdsAndUserId(tripIds, "u-1")).thenReturn(List.of(
                new ExpenseMapper.TripAmountSummary("t-1", new BigDecimal("100.00"))
        ));
        when(expenseMapper.monthlyVisibleByTripIdsAndUserId(eq(tripIds), eq("u-1"), any(), any()))
                .thenReturn(List.of());

        var resp = statsService.getOverview(null);

        assertEquals(1, resp.overview().tripCount());
        assertEquals(5, resp.overview().itineraryCount());
        assertEquals("100.00", resp.overview().totalExpenseAmount());

        verify(itineraryNodeMapper).countByUserIdAndTripIds("u-1", tripIds);
        verify(expenseMapper, atLeastOnce()).sumVisibleAmountCnyByTripIdsAndUserId(tripIds, "u-1");
    }

    @Test
    void getOverview_singleTrip_consistentWithSummaryStats() {
        LoginUserContext.setUserId("u-1");

        SummaryResp summary = createSummaryResp();
        when(summaryService.getSummary("trip-1")).thenReturn(summary);
        when(expenseMapper.dailyVisibleByTripIdAndUserId("trip-1", "u-1")).thenReturn(List.of());

        var resp = statsService.getOverview("trip-1");

        assertEquals(summary.stats().itineraryCount(), resp.overview().itineraryCount());
        assertEquals(summary.stats().journalCount(), resp.overview().journalCount());
        assertEquals(summary.stats().photoCount(), resp.overview().photoCount());
        assertEquals(summary.stats().expenseCount(), resp.overview().expenseCount());
        assertEquals(summary.stats().totalExpenseAmount(), resp.overview().totalExpenseAmount());
        assertEquals(summary.expenseSummary().totalAmount(), resp.expenseStats().totalAmount());
    }

    @Test
    void getOverview_singleTrip_includesLegacyExpenseOnlyDateInDailyTrend() {
        LoginUserContext.setUserId("u-1");

        SummaryResp summary = createSummaryResp();
        when(summaryService.getSummary("trip-1")).thenReturn(summary);
        when(expenseMapper.dailyVisibleByTripIdAndUserId("trip-1", "u-1")).thenReturn(List.of(
                new ExpenseMapper.DailyAmountSummary(LocalDate.of(2026, 7, 6), new BigDecimal("88.00"))
        ));

        var resp = statsService.getOverview("trip-1");

        assertEquals(2, resp.dailyTrend().size());
        assertEquals("2026-07-06", resp.dailyTrend().get(1).date());
        assertEquals("88.00", resp.dailyTrend().get(1).expenseAmount());
        assertNull(resp.dailyTrend().get(1).journalCount());
        assertNull(resp.dailyTrend().get(1).photoCount());
    }

    @Test
    void getOverview_global_neverCallsSingleTripSummary() {
        LoginUserContext.setUserId("u-1");
        when(tripMapper.selectList(any())).thenReturn(List.of(
                trip("t-1", "test", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 3))
        ));
        List<String> tripIds = List.of("t-1");
        when(itineraryNodeMapper.countByUserIdAndTripIds("u-1", tripIds)).thenReturn(0L);
        when(journalEntryMapper.countVisibleByTripIdsAndUserId(tripIds, "u-1")).thenReturn(0L);
        when(journalPhotoMapper.countVisibleUploadedPhotoByTripIdsAndUserId(tripIds, "u-1")).thenReturn(0L);
        when(expenseMapper.categoryVisibleByTripIdsAndUserId(tripIds, "u-1")).thenReturn(List.of());
        when(expenseMapper.sumVisibleAmountCnyByTripIdsAndUserId(tripIds, "u-1")).thenReturn(List.of());
        when(expenseMapper.monthlyVisibleByTripIdsAndUserId(eq(tripIds), eq("u-1"), any(), any()))
                .thenReturn(List.of());

        statsService.getOverview(null);

        verify(summaryService, never()).getSummary(any());
    }

    @Test
    void getOverview_singleTrip_noImageUrlsLeak() throws Exception {
        LoginUserContext.setUserId("u-1");

        SummaryResp summary = createSummaryResp();
        when(summaryService.getSummary("trip-1")).thenReturn(summary);
        when(expenseMapper.dailyVisibleByTripIdAndUserId("trip-1", "u-1")).thenReturn(List.of());

        var resp = statsService.getOverview("trip-1");
        String json = new ObjectMapper().writeValueAsString(resp);

        assertFalse(json.contains("imageUrls"));
        assertFalse(json.contains("image_urls"));
        assertFalse(json.contains("highlightLogs"));
        assertFalse(json.contains("featuredPhotos"));
        assertFalse(json.contains("memoryReadiness"));
        assertFalse(json.contains("dataGaps"));
        assertFalse(json.contains("nextSteps"));
        assertFalse(json.contains("score"));
    }

    @Test
    void getOverview_global_withNullExpense_UsesZero() {
        LoginUserContext.setUserId("u-1");
        when(tripMapper.selectList(any())).thenReturn(List.of(
                trip("t-1", "test", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 2))
        ));
        List<String> tripIds = List.of("t-1");
        when(itineraryNodeMapper.countByUserIdAndTripIds("u-1", tripIds)).thenReturn(0L);
        when(journalEntryMapper.countVisibleByTripIdsAndUserId(tripIds, "u-1")).thenReturn(0L);
        when(journalPhotoMapper.countVisibleUploadedPhotoByTripIdsAndUserId(tripIds, "u-1")).thenReturn(0L);
        when(expenseMapper.categoryVisibleByTripIdsAndUserId(tripIds, "u-1")).thenReturn(List.of());
        when(expenseMapper.sumVisibleAmountCnyByTripIdsAndUserId(tripIds, "u-1")).thenReturn(List.of(
                new ExpenseMapper.TripAmountSummary("t-1", null)
        ));
        when(expenseMapper.monthlyVisibleByTripIdsAndUserId(eq(tripIds), eq("u-1"), any(), any()))
                .thenReturn(List.of());

        var resp = statsService.getOverview(null);

        assertEquals("0.00", resp.overview().totalExpenseAmount());
        assertEquals("0.00", resp.overview().averageDailyExpense());
        assertEquals("0.00", resp.recentTrips().getFirst().totalExpenseAmount());
    }

    @Test
    void getSpendAnalysis_global_returnsSpendDetail() {
        LoginUserContext.setUserId("u-1");
        LocalDate today = LocalDate.now();
        when(tripMapper.selectList(any())).thenReturn(List.of(
                trip("t-1", "京都五日旅行", today.minusDays(4), today)
        ));
        List<String> tripIds = List.of("t-1");
        when(expenseMapper.categoryVisibleByTripIdsAndUserId(tripIds, "u-1")).thenReturn(List.of(
                new ExpenseMapper.CategorySummary("hotel", new BigDecimal("3000.00"), 1),
                new ExpenseMapper.CategorySummary("food", new BigDecimal("2000.00"), 4)
        ));
        when(expenseMapper.sumVisibleAmountCnyByTripIdsAndUserId(tripIds, "u-1")).thenReturn(List.of(
                new ExpenseMapper.TripAmountSummary("t-1", new BigDecimal("5000.00"))
        ));

        var resp = statsService.getSpendAnalysis(null);

        assertEquals("5000.00", resp.totalAmount());
        assertEquals("1000.00", resp.averageDailyExpense());
        assertEquals("hotel", resp.topCategory());
        assertEquals(1, resp.tripEfficiency().size());
    }

    @Test
    void getRecordsAnalysis_singleTrip_returnsRecordFacts() {
        LoginUserContext.setUserId("u-1");
        when(summaryService.getSummary("trip-1")).thenReturn(createSummaryResp());

        var resp = statsService.getRecordsAnalysis("trip-1");

        assertEquals(8, resp.journalCount());
        assertEquals("trip-1", resp.tripRecords().getFirst().tripId());
    }

    @Test
    void getFootprintAnalysis_global_returnsCitiesAndNodeTypes() {
        LoginUserContext.setUserId("u-1");
        Trip trip = trip("t-1", "京都五日旅行", LocalDate.now().minusDays(4), LocalDate.now());
        trip.setDestinationName("京都市");
        trip.setDestinationAdcode("260000");
        when(tripMapper.selectList(any())).thenReturn(List.of(trip));
        List<String> tripIds = List.of("t-1");
        when(itineraryNodeMapper.countByUserIdAndTripIds("u-1", tripIds)).thenReturn(5L);
        when(itineraryNodeMapper.countByTripIdsAndUserId(tripIds, "u-1")).thenReturn(List.of(
                new ItineraryNodeMapper.TripCountSummary("t-1", 5)
        ));
        when(itineraryNodeMapper.nodeTypeSummaryByUserIdAndTripIds("u-1", tripIds)).thenReturn(List.of(
                new ItineraryNodeMapper.NodeTypeSummary("attraction", 3)
        ));

        var resp = statsService.getFootprintAnalysis(null);

        assertEquals(1, resp.destinationCount());
        assertEquals("京都市", resp.cities().getFirst().name());
        assertEquals(5, resp.cities().getFirst().itineraryCount());
        assertEquals("景点", resp.nodeTypes().getFirst().typeName());
    }

    private Trip trip(String id, String title, LocalDate start, LocalDate end) {
        Trip trip = new Trip();
        trip.setId(id);
        trip.setUserId("u-1");
        trip.setTitle(title);
        trip.setStartDate(start);
        trip.setEndDate(end);
        trip.setStatus("planning");
        return trip;
    }

    private SummaryResp createSummaryResp() {
        return new SummaryResp(
                new SummaryResp.TripInfo(
                        "trip-1",
                        "京都五日旅行",
                        "京都五日旅行",
                        "2026-07-01",
                        "2026-07-05",
                        5,
                        "completed",
                        null
                ),
                new SummaryResp.Stats(
                        5,
                        12,
                        8,
                        24,
                        34,
                        "8560.00",
                        "CNY",
                        3
                ),
                new SummaryResp.ExpenseSummary(
                        "8560.00",
                        "CNY",
                        List.of(
                                new SummaryResp.ExpenseCategoryStat(
                                        "food", "餐饮", "5000.00", "58.41", 20),
                                new SummaryResp.ExpenseCategoryStat(
                                        "transport", "交通", "3560.00", "41.59", 14)
                        )
                ),
                List.of(),
                List.of(),
                List.of(
                        new SummaryResp.DailyTimelineItem(
                                "2026-07-01", 1, List.of(), List.of(
                                new SummaryResp.LogItem(
                                        "log-1",
                                        "第一天",
                                        "京都",
                                        "2026-07-01T10:00:00Z",
                                        true,
                                        List.of(
                                                new SummaryResp.PhotoItem("file-1", "log-1", "url-1", "url-1", "url-1", "url-1", "url-1", "url-1", "url-1", null, 100, 100, 1),
                                                new SummaryResp.PhotoItem("file-2", "log-1", "url-2", "url-2", "url-2", "url-2", "url-2", "url-2", "url-2", null, 100, 100, 2)
                                        ))
                        )),
                        new SummaryResp.DailyTimelineItem(
                                "2026-07-02", 2, List.of(), List.of()),
                        new SummaryResp.DailyTimelineItem(
                                "2026-07-03", 3, List.of(), List.of()),
                        new SummaryResp.DailyTimelineItem(
                                "2026-07-04", 4, List.of(), List.of()),
                        new SummaryResp.DailyTimelineItem(
                                "2026-07-05", 5, List.of(), List.of())
                )
        );
    }
}
