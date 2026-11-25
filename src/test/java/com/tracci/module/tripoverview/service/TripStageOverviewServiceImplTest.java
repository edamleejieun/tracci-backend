package com.tracci.module.tripoverview.service;

import com.tracci.common.context.LoginUserContext;
import com.tracci.module.expense.repository.ExpenseMapper;
import com.tracci.module.summary.dto.response.SummaryResp;
import com.tracci.module.summary.service.SummaryService;
import com.tracci.module.trip.dto.response.TripWeatherResponse;
import com.tracci.module.trip.entity.Trip;
import com.tracci.module.trip.entity.TripMember;
import com.tracci.module.trip.enums.TripPermission;
import com.tracci.module.trip.repository.TripMapper;
import com.tracci.module.trip.service.TripAccessService;
import com.tracci.module.trip.service.TripWeatherService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doReturn;
import static com.tracci.testsupport.TripAccessTestSupport.stubOwnerAccess;

@ExtendWith(MockitoExtension.class)
class TripStageOverviewServiceImplTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 6, 22);

    @Mock
    private TripMapper tripMapper;
    @Mock
    private TripAccessService tripAccessService;
    @Mock
    private SummaryService summaryService;
    @Mock
    private ExpenseMapper expenseMapper;
    @Mock
    private TripWeatherService tripWeatherService;

    private TripStageOverviewServiceImpl service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-06-22T04:00:00Z"), ZoneId.of("Asia/Shanghai"));
        service = new TripStageOverviewServiceImpl(
                tripMapper, tripAccessService, summaryService, expenseMapper, tripWeatherService, clock);
        stubOwnerAccess(tripAccessService, tripMapper);
        LoginUserContext.setUserId("u-1");
    }

    @AfterEach
    void tearDown() {
        LoginUserContext.clear();
    }

    @Test
    void noTrip_returnsGrowingPageEntryAction() {
        when(tripMapper.listMemberTrips("u-1")).thenReturn(List.of());

        var response = service.getOverview(null, "IOS", "Asia/Shanghai");

        assertEquals("NO_TRIP", response.phase());
        assertNull(response.focusTrip());
        assertEquals("NO_TRIP", response.selection().reason());
        assertEquals("CREATE_TRIP", response.actions().getFirst().type());
    }

    @Test
    void invalidTimeZone_fallsBackExplicitly() {
        when(tripMapper.listMemberTrips("u-1")).thenReturn(List.of());

        var response = service.getOverview(null, "IOS", "Mars/Olympus");

        assertTrue(response.timeZoneFallbackApplied());
        assertEquals("Asia/Shanghai", response.timeZone());
    }

    @Test
    void generatedDate_usesRequestedIanaTimeZoneAtDateBoundary() {
        Clock boundaryClock = Clock.fixed(Instant.parse("2026-06-22T15:30:00Z"), ZoneId.of("UTC"));
        TripStageOverviewServiceImpl boundaryService = new TripStageOverviewServiceImpl(
                tripMapper, tripAccessService, summaryService, expenseMapper, tripWeatherService, boundaryClock);
        when(tripMapper.listMemberTrips("u-1")).thenReturn(List.of());

        var response = boundaryService.getOverview(null, "IOS", "Asia/Tokyo");

        assertEquals("2026-06-23", response.generatedDate());
        assertEquals("Asia/Tokyo", response.timeZone());
    }

    @Test
    void planning_returnsScheduleDensityWeatherAndUnplannedDates() {
        Trip trip = trip("planning", TODAY.plusDays(3), TODAY.plusDays(5));
        when(tripMapper.listMemberTrips("u-1")).thenReturn(List.of(trip));
        when(summaryService.getSummary("planning")).thenReturn(summary(trip, List.of(
                day("2026-06-25", 1, 2, 0, 0),
                day("2026-06-26", 2, 0, 0, 0),
                day("2026-06-27", 3, 4, 0, 0)), 0, 0, 0, "0.00"));
        when(expenseMapper.dailyVisibleByTripIdAndUserId("planning", "u-1")).thenReturn(List.of());
        when(tripWeatherService.getTripWeather("planning", "IOS", "Asia/Shanghai")).thenReturn(
                new TripWeatherResponse(false, "DATE_TOO_FAR", "AMAP", "DAILY", "暂未到预报窗口",
                        TODAY, "杭州", List.of(), null));

        var response = service.getOverview(null, "ios", "Asia/Shanghai");

        assertEquals("PLANNING", response.phase());
        assertEquals("NEAREST_PLANNING_TRIP", response.selection().reason());
        assertEquals(2, response.planning().scheduledDayCount());
        assertEquals(List.of("2026-06-26"), response.planning().unplannedDates());
        assertEquals("MODERATE", response.planning().density().level());
        assertEquals(3, response.planning().departureDays());
        assertEquals("DATE_TOO_FAR", response.planning().weather().status());
        assertEquals("Asia/Shanghai", response.timeZone());
        assertFalse(response.timeZoneFallbackApplied());
    }

    @Test
    void ongoing_prefersCurrentTripAndOnlyReportsElapsedDateGaps() {
        Trip ongoing = trip("ongoing", TODAY.minusDays(1), TODAY.plusDays(1));
        Trip planning = trip("planning", TODAY.plusDays(2), TODAY.plusDays(4));
        when(tripMapper.listMemberTrips("u-1")).thenReturn(List.of(planning, ongoing));
        when(summaryService.getSummary("ongoing")).thenReturn(summary(ongoing, List.of(
                day("2026-06-21", 1, 2, 0, 0),
                day("2026-06-22", 2, 3, 1, 2),
                day("2026-06-23", 3, 1, 0, 0)), 1, 2, 1, "66.00"));
        when(expenseMapper.dailyVisibleByTripIdAndUserId("ongoing", "u-1")).thenReturn(List.of(
                new ExpenseMapper.DailyAmountSummary(TODAY, new BigDecimal("66.00"))));

        var response = service.getOverview(null, "IOS", "Asia/Shanghai");

        assertEquals("ONGOING", response.phase());
        assertEquals("ONGOING_TRIP", response.selection().reason());
        assertEquals(3, response.ongoing().today().itineraryCount());
        assertEquals("66.00", response.ongoing().today().expenseAmount());
        assertEquals(2, response.ongoing().today().photoCount());
        assertTrue(response.permissions().canAddJournal());
        assertTrue(response.permissions().canFeaturePhoto());
        assertEquals(1, response.ongoing().suggestions().size());
        assertEquals("2026-06-21", response.ongoing().suggestions().getFirst().date());
        assertEquals("ongoing:2026-06-21:MEMORY_FRAGMENT",
                response.ongoing().suggestions().getFirst().suggestionKey());
    }

    @Test
    void completed_returnsExpensePaceAndStoryEntry() {
        Trip trip = trip("completed", TODAY.minusDays(4), TODAY.minusDays(2));
        when(tripMapper.listMemberTrips("u-1")).thenReturn(List.of(trip));
        when(summaryService.getSummary("completed")).thenReturn(summary(trip, List.of(
                day("2026-06-18", 1, 2, 1, 3),
                day("2026-06-19", 2, 5, 1, 1),
                day("2026-06-20", 3, 0, 0, 0)), 2, 4, 1, "300.00"));
        when(expenseMapper.dailyVisibleByTripIdAndUserId("completed", "u-1")).thenReturn(List.of(
                new ExpenseMapper.DailyAmountSummary(LocalDate.of(2026, 6, 18), new BigDecimal("100.00")),
                new ExpenseMapper.DailyAmountSummary(LocalDate.of(2026, 6, 19), new BigDecimal("200.00"))));

        var response = service.getOverview(null, "IOS", "Asia/Shanghai");

        assertEquals("COMPLETED", response.phase());
        assertEquals("300.00", response.completed().totalExpenseAmount());
        assertEquals("100.00", response.completed().averageDailyExpense());
        assertEquals("2026-06-19", response.completed().pace().busiestDate());
        assertEquals("RELAXED", response.completed().pace().level());
        assertTrue(response.actions().stream().anyMatch(action -> "VIEW_STORY".equals(action.type())));
    }

    @Test
    void completed_withoutJournals_doesNotFailHighlights() {
        Trip trip = trip("journal-less-completed", TODAY.minusDays(2), TODAY.minusDays(1));
        when(tripMapper.listMemberTrips("u-1")).thenReturn(List.of(trip));
        when(summaryService.getSummary("journal-less-completed")).thenReturn(summary(trip, List.of(
                day("2026-06-20", 1, 2, 0, 0),
                day("2026-06-21", 2, 1, 0, 0)), 0, 0, 0, "0.00"));
        when(expenseMapper.dailyVisibleByTripIdAndUserId("journal-less-completed", "u-1")).thenReturn(List.of());

        var response = service.getOverview(null, "IOS", "Asia/Shanghai");

        assertEquals("COMPLETED", response.phase());
        assertNull(response.completed().highlights().representativeLog());
    }

    @Test
    void completed_withOneJournalAndNoPhotos_usesLightweightPresentationButKeepsStoryEntry() {
        Trip trip = trip("light-completed", TODAY.minusDays(2), TODAY.minusDays(1));
        when(tripMapper.listMemberTrips("u-1")).thenReturn(List.of(trip));
        when(summaryService.getSummary("light-completed")).thenReturn(summary(trip, List.of(
                day("2026-06-20", 1, 1, 1, 0),
                day("2026-06-21", 2, 0, 0, 0)), 1, 0, 0, "0.00"));
        when(expenseMapper.dailyVisibleByTripIdAndUserId("light-completed", "u-1")).thenReturn(List.of());

        var response = service.getOverview(null, "IOS", "Asia/Shanghai");

        assertEquals("LIGHTWEIGHT", response.memory().displayableContent().presentation());
        assertTrue(response.actions().stream().anyMatch(action -> "VIEW_STORY".equals(action.type())));
    }

    @Test
    void ongoing_viewerGetsReadOnlyPermissionsAndNoWriteSuggestionOrAction() {
        Trip trip = trip("viewer-trip", TODAY.minusDays(1), TODAY.plusDays(1));
        trip.setUserId("owner-user");
        TripMember member = new TripMember();
        member.setId("member-viewer");
        member.setTripId("viewer-trip");
        member.setUserId("u-1");
        member.setRole("viewer");
        when(tripMapper.listMemberTrips("u-1")).thenReturn(List.of(trip));
        doReturn(new TripAccessService.TripAccess(trip, member))
                .when(tripAccessService)
                .requirePermission("viewer-trip", "u-1", TripPermission.READ);
        when(summaryService.getSummary("viewer-trip")).thenReturn(summary(trip, List.of(
                day("2026-06-21", 1, 2, 0, 0),
                day("2026-06-22", 2, 3, 0, 0),
                day("2026-06-23", 3, 1, 0, 0)), 0, 0, 0, "0.00"));
        when(expenseMapper.dailyVisibleByTripIdAndUserId("viewer-trip", "u-1")).thenReturn(List.of());

        var response = service.getOverview(null, "IOS", "Asia/Shanghai");

        assertFalse(response.permissions().canAddJournal());
        assertFalse(response.permissions().canFeaturePhoto());
        assertTrue(response.ongoing().suggestions().isEmpty());
        assertTrue(response.actions().stream().noneMatch(action -> "ADD_MEMORY".equals(action.type())));
    }

    private Trip trip(String id, LocalDate startDate, LocalDate endDate) {
        Trip trip = new Trip();
        trip.setId(id);
        trip.setUserId("u-1");
        trip.setTitle("杭州旅程");
        trip.setDestinationName("杭州");
        trip.setStartDate(startDate);
        trip.setEndDate(endDate);
        return trip;
    }

    private SummaryResp summary(
            Trip trip,
            List<SummaryResp.DailyTimelineItem> days,
            long journalCount,
            long photoCount,
            long highlightCount,
            String expense) {
        long itineraryCount = days.stream().mapToLong(day -> day.itineraries().size()).sum();
        return new SummaryResp(
                new SummaryResp.TripInfo(
                        trip.getId(), trip.getTitle(), trip.getDestinationName(), trip.getStartDate().toString(),
                        trip.getEndDate().toString(), days.size(), "", null),
                new SummaryResp.Stats(days.size(), itineraryCount, journalCount, photoCount, 1, expense, "CNY", highlightCount),
                new SummaryResp.ExpenseSummary(expense, "CNY", List.of(
                        new SummaryResp.ExpenseCategoryStat("food", "餐饮", expense, "100.00", 1))),
                List.of(),
                photoCount > 0 ? List.of(photo("recommended")) : List.of(),
                days);
    }

    private SummaryResp.DailyTimelineItem day(
            String date,
            int dayIndex,
            int itineraryCount,
            int journalCount,
            int photoCount) {
        List<SummaryResp.TimelineItineraryItem> itineraries = java.util.stream.IntStream.range(0, itineraryCount)
                .mapToObj(index -> new SummaryResp.TimelineItineraryItem(
                        "n-" + dayIndex + "-" + index, "attraction", "节点", "地点", "09:00", "10:00"))
                .toList();
        List<SummaryResp.LogItem> logs = java.util.stream.IntStream.range(0, journalCount)
                .mapToObj(index -> new SummaryResp.LogItem(
                        "j-" + dayIndex + "-" + index,
                        "记录",
                        "地点",
                        date + "T10:00:00Z",
                        false,
                        index == 0
                                ? java.util.stream.IntStream.range(0, photoCount).mapToObj(i -> photo("p-" + i)).toList()
                                : List.of()))
                .toList();
        return new SummaryResp.DailyTimelineItem(date, dayIndex, itineraries, logs);
    }

    private SummaryResp.PhotoItem photo(String id) {
        return new SummaryResp.PhotoItem(
                id, "journal", "url", null, null, null, null, null, null, null, 1080, 1440, 0);
    }
}
