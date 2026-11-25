package com.tracci.module.export.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tracci.common.context.LoginUserContext;
import com.tracci.common.exception.AuthException;
import com.tracci.common.exception.BizException;
import com.tracci.common.exception.ForbiddenException;
import com.tracci.common.exception.NotFoundException;
import com.tracci.module.export.dto.response.ExportAssetsResp;
import com.tracci.module.export.dto.response.ItineraryExportAssetsResp;
import com.tracci.module.summary.dto.response.SummaryResp;
import com.tracci.module.summary.service.SummaryService;
import com.tracci.module.trip.dto.response.ItineraryDayResp;
import com.tracci.module.trip.dto.response.ItineraryNodeBriefResp;
import com.tracci.module.trip.dto.response.ItineraryNodeLocationResp;
import com.tracci.module.trip.dto.response.ItineraryResp;
import com.tracci.module.trip.entity.Trip;
import com.tracci.module.trip.enums.ItineraryNodeType;
import com.tracci.module.trip.repository.ItineraryNodeMapper;
import com.tracci.module.trip.repository.TripMapper;
import com.tracci.module.trip.service.ItineraryService;
import com.tracci.module.trip.service.TripAccessService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;
import static com.tracci.testsupport.TripAccessTestSupport.stubOwnerAccess;

@ExtendWith(MockitoExtension.class)
class ExportServiceImplTest {

    @Mock
    private SummaryService summaryService;

    @Mock
    private ItineraryNodeMapper itineraryNodeMapper;

    @Mock
    private ItineraryService itineraryService;

    @Mock
    private TripMapper tripMapper;
    @Mock
    private TripAccessService tripAccessService;

    @InjectMocks
    private ExportServiceImpl exportService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        LoginUserContext.setUserId("user-1");
        stubOwnerAccess(tripAccessService, tripMapper);
        lenient().when(itineraryNodeMapper.selectAttractionTitlesByTripIdAndUserId(
                        "trip-1", "user-1", ItineraryNodeType.ATTRACTION.value()))
                .thenReturn(List.of());
    }

    @AfterEach
    void tearDown() {
        LoginUserContext.clear();
    }

    @Test
    void getExportAssets_returnsExportDataForOwnTrip() throws Exception {
        when(summaryService.getSummary("trip-1")).thenReturn(summaryWithFullData());

        ExportAssetsResp resp = exportService.getExportAssets("trip-1", null, null);

        assertEquals("trip-1", resp.trip().id());
        assertEquals("京都五日旅行", resp.trip().title());
        assertEquals("京都五日旅行", resp.trip().destination());
        assertEquals("2026-05-01", resp.trip().startDate());
        assertEquals("2026-05-05", resp.trip().endDate());
        assertEquals(5, resp.trip().totalDays());
        assertEquals("https://cdn.example.com/photo-2.jpg", resp.trip().coverPhoto());

        assertEquals(12, resp.stats().itineraryCount());
        assertEquals(8, resp.stats().journalCount());
        assertEquals(24, resp.stats().photoCount());
        assertEquals(34, resp.stats().expenseCount());
        assertEquals(new BigDecimal("5860.50"), resp.stats().totalExpenseAmount());
        assertEquals(new BigDecimal("1172.10"), resp.stats().averageDailyExpense());
        assertEquals("CNY", resp.stats().currency());

        assertEquals(3, resp.photos().size());
        assertEquals("file-2", resp.photos().get(0).fileId());
        assertEquals(1, resp.photos().get(0).sortOrder());
        assertEquals("file-1", resp.photos().get(1).fileId());
        assertEquals(2, resp.photos().get(1).sortOrder());
        assertEquals("file-3", resp.photos().get(2).fileId());
        assertEquals(3, resp.photos().get(2).sortOrder());

        assertEquals(3, resp.expenseCategoryStats().size());
        assertEquals("food", resp.expenseCategoryStats().get(0).category());
        assertEquals("餐饮", resp.expenseCategoryStats().get(0).categoryName());
        assertEquals(new BigDecimal("2100.00"), resp.expenseCategoryStats().get(0).amount());
        assertEquals(new BigDecimal("35.83"), resp.expenseCategoryStats().get(0).percentage());
        assertEquals(12, resp.expenseCategoryStats().get(0).count());
        assertEquals("transport", resp.expenseCategoryStats().get(1).category());

        assertTrue(resp.watermark().enabled());
        assertEquals("by Tracci", resp.watermark().text());

        assertEquals("travel-cover", resp.exportOptions().template());
        assertEquals("square", resp.exportOptions().format());
        assertTrue(resp.visitedPlaces().isEmpty());
        assertTrue(resp.exportOptions().contentOptions().showDate());
        assertTrue(resp.exportOptions().contentOptions().showWatermark());
        assertEquals("none", resp.exportOptions().contentOptions().copyStyle());

        String json = objectMapper.writeValueAsString(resp);
        assertFalse(json.contains("imageUrls"));
        assertFalse(json.contains("objectKey"));
        assertFalse(json.contains("bucket"));
        assertFalse(json.contains("region"));
        assertFalse(json.contains("secret"));
        assertFalse(json.contains("etag"));
        assertFalse(json.contains("dayCount"));
    }

    @Test
    void getExportAssets_propagatesForbiddenException() {
        when(summaryService.getSummary("trip-1"))
                .thenThrow(new ForbiddenException());

        assertThrows(ForbiddenException.class,
                () -> exportService.getExportAssets("trip-1", null, null));
    }

    @Test
    void getExportAssets_propagatesAuthException() {
        LoginUserContext.clear();

        assertThrows(AuthException.class,
                () -> exportService.getExportAssets("trip-1", null, null));
    }

    @Test
    void getExportAssets_defaultsTemplateToTravelCover() {
        when(summaryService.getSummary("trip-1")).thenReturn(summaryWithFullData());

        ExportAssetsResp resp = exportService.getExportAssets("trip-1", null, null);
        assertEquals("travel-cover", resp.exportOptions().template());

        ExportAssetsResp respBlank = exportService.getExportAssets("trip-1", "", null);
        assertEquals("travel-cover", respBlank.exportOptions().template());
    }

    @Test
    void getExportAssets_defaultsFormatToSquare() {
        when(summaryService.getSummary("trip-1")).thenReturn(summaryWithFullData());

        ExportAssetsResp resp = exportService.getExportAssets("trip-1", null, null);
        assertEquals("square", resp.exportOptions().format());

        ExportAssetsResp respBlank = exportService.getExportAssets("trip-1", null, "");
        assertEquals("square", respBlank.exportOptions().format());
    }

    @Test
    void getExportAssets_rejectsInvalidTemplate() {
        BizException ex = assertThrows(BizException.class,
                () -> exportService.getExportAssets("trip-1", "invalid_template", null));
        assertTrue(ex.getMessage().contains("无效的模板参数"));
        assertTrue(ex.getMessage().contains("travel-cover / film-collage / minimal-summary"));
    }

    @Test
    void getExportAssets_rejectsInvalidFormat() {
        BizException ex = assertThrows(BizException.class,
                () -> exportService.getExportAssets("trip-1", null, "landscape"));
        assertTrue(ex.getMessage().contains("无效的格式参数"));
    }

    @Test
    void getExportAssets_acceptsAllValidTemplates() {
        when(summaryService.getSummary("trip-1")).thenReturn(summaryWithFullData());

        assertEquals("travel-cover",
                exportService.getExportAssets("trip-1", "travel-cover", null).exportOptions().template());
        assertEquals("film-collage",
                exportService.getExportAssets("trip-1", "film-collage", null).exportOptions().template());
        assertEquals("minimal-summary",
                exportService.getExportAssets("trip-1", "minimal-summary", null).exportOptions().template());
    }

    @Test
    void getExportAssets_rejectsLegacyTemplates() {
        assertThrows(BizException.class,
                () -> exportService.getExportAssets("trip-1", "clean_white", null));
        assertThrows(BizException.class,
                () -> exportService.getExportAssets("trip-1", "film_brown", null));
        assertThrows(BizException.class,
                () -> exportService.getExportAssets("trip-1", "midnight-blue", null));
    }

    @Test
    void getExportAssets_acceptsAllValidFormats() {
        when(summaryService.getSummary("trip-1")).thenReturn(summaryWithFullData());

        assertEquals("square",
                exportService.getExportAssets("trip-1", null, "square").exportOptions().format());
        assertEquals("portrait",
                exportService.getExportAssets("trip-1", null, "portrait").exportOptions().format());
    }

    @Test
    void getExportAssets_capsPhotosAtThree() {
        when(summaryService.getSummary("trip-1")).thenReturn(summaryWithManyPhotos());

        ExportAssetsResp resp = exportService.getExportAssets("trip-1", null, null);
        assertEquals(3, resp.photos().size());
    }

    @Test
    void getExportAssets_returnsEmptyPhotosAndNullCoverForNoPhotos() {
        when(summaryService.getSummary("trip-1")).thenReturn(summaryWithEmptyPhotos());

        ExportAssetsResp resp = exportService.getExportAssets("trip-1", null, null);
        assertTrue(resp.photos().isEmpty());
        assertNull(resp.trip().coverPhoto());
    }

    @Test
    void getExportAssets_returnsFewerThanThreePhotosWhenAvailable() {
        when(summaryService.getSummary("trip-1")).thenReturn(summaryWithTwoPhotos());

        ExportAssetsResp resp = exportService.getExportAssets("trip-1", null, null);
        assertEquals(2, resp.photos().size());
        assertEquals(1, resp.photos().get(0).sortOrder());
        assertEquals(2, resp.photos().get(1).sortOrder());
    }

    @Test
    void getExportAssets_capsExpenseCategoriesAtThree() {
        when(summaryService.getSummary("trip-1")).thenReturn(summaryWithManyCategories());

        ExportAssetsResp resp = exportService.getExportAssets("trip-1", null, null);
        assertEquals(3, resp.expenseCategoryStats().size());
    }

    @Test
    void getExportAssets_returnsExpenseCategoriesInAmountDescOrder() {
        when(summaryService.getSummary("trip-1")).thenReturn(summaryWithManyCategories());

        ExportAssetsResp resp = exportService.getExportAssets("trip-1", null, null);
        assertEquals(3, resp.expenseCategoryStats().size());
        assertEquals("hotel", resp.expenseCategoryStats().get(0).category());
        assertEquals("food", resp.expenseCategoryStats().get(1).category());
        assertEquals("transport", resp.expenseCategoryStats().get(2).category());
    }

    @Test
    void getExportAssets_returnsZeroForNoExpenses() {
        when(summaryService.getSummary("trip-1")).thenReturn(summaryWithNoExpenses());

        ExportAssetsResp resp = exportService.getExportAssets("trip-1", null, null);
        assertEquals(0, resp.stats().expenseCount());
        assertEquals(new BigDecimal("0.00"), resp.stats().totalExpenseAmount());
        assertEquals(new BigDecimal("0.00"), resp.stats().averageDailyExpense());
        assertTrue(resp.expenseCategoryStats().isEmpty());
    }

    @Test
    void getExportAssets_returnsZeroAverageDailyExpenseWhenZeroDays() {
        when(summaryService.getSummary("trip-1")).thenReturn(summaryWithZeroDays());

        ExportAssetsResp resp = exportService.getExportAssets("trip-1", null, null);
        assertEquals(0, resp.trip().totalDays());
        assertEquals(new BigDecimal("0.00"), resp.stats().averageDailyExpense());
    }

    @Test
    void getExportAssets_calculatesAverageDailyExpenseCorrectly() {
        when(summaryService.getSummary("trip-1")).thenReturn(summaryWithCustomAmount("3000.00", 3));

        ExportAssetsResp resp = exportService.getExportAssets("trip-1", null, null);
        assertEquals(new BigDecimal("1000.00"), resp.stats().averageDailyExpense());
    }

    @Test
    void getExportAssets_echoesTemplateAndFormat() {
        when(summaryService.getSummary("trip-1")).thenReturn(summaryWithFullData());

        ExportAssetsResp resp = exportService.getExportAssets("trip-1", "film-collage", "portrait");
        assertEquals("film-collage", resp.exportOptions().template());
        assertEquals("portrait", resp.exportOptions().format());
    }

    @Test
    void getExportAssets_returnsVisitedPlaces() {
        when(summaryService.getSummary("trip-1")).thenReturn(summaryWithFullData());
        when(itineraryNodeMapper.selectAttractionTitlesByTripIdAndUserId(
                        "trip-1", "user-1", ItineraryNodeType.ATTRACTION.value()))
                .thenReturn(List.of("清水寺", "伏见稻荷大社", "岚山"));

        ExportAssetsResp resp = exportService.getExportAssets("trip-1", null, null);

        assertEquals(List.of("清水寺", "伏见稻荷大社", "岚山"), resp.visitedPlaces());
    }

    @Test
    void getExportAssets_returnsDefaultContentOptions() {
        when(summaryService.getSummary("trip-1")).thenReturn(summaryWithFullData());

        ExportAssetsResp.ContentOptions options = exportService.getExportAssets("trip-1", null, null)
                .exportOptions()
                .contentOptions();

        assertTrue(options.showDate());
        assertTrue(options.showDays());
        assertTrue(options.showItineraryCount());
        assertTrue(options.showJournalCount());
        assertTrue(options.showExpense());
        assertTrue(options.showVisitedPlaces());
        assertTrue(options.showExpenseDistribution());
        assertTrue(options.showWatermark());
        assertEquals("none", options.copyStyle());
    }

    @Test
    void getExportAssets_watermarkAlwaysEnabledWithCorrectText() {
        when(summaryService.getSummary("trip-1")).thenReturn(summaryWithFullData());

        ExportAssetsResp resp = exportService.getExportAssets("trip-1", null, null);
        assertTrue(resp.watermark().enabled());
        assertEquals("by Tracci", resp.watermark().text());
    }

    @Test
    void getExportAssets_setsCoverPhotoFromFirstFeaturedPhoto() {
        when(summaryService.getSummary("trip-1")).thenReturn(summaryWithFullData());

        ExportAssetsResp resp = exportService.getExportAssets("trip-1", null, null);
        assertEquals("https://cdn.example.com/photo-2.jpg", resp.trip().coverPhoto());
    }

    @Test
    void getExportAssets_usesTotalDaysNotDayCount() throws Exception {
        when(summaryService.getSummary("trip-1")).thenReturn(summaryWithFullData());

        ExportAssetsResp resp = exportService.getExportAssets("trip-1", null, null);
        assertEquals(5, resp.trip().totalDays());

        String json = objectMapper.writeValueAsString(resp);
        assertTrue(json.contains("totalDays"));
        assertFalse(json.contains("dayCount"));
    }

    @Test
    void getExportAssets_statsConsistentWithSummary() {
        when(summaryService.getSummary("trip-1")).thenReturn(summaryWithFullData());

        ExportAssetsResp resp = exportService.getExportAssets("trip-1", null, null);
        assertEquals(12, resp.stats().itineraryCount());
        assertEquals(8, resp.stats().journalCount());
        assertEquals(24, resp.stats().photoCount());
        assertEquals(34, resp.stats().expenseCount());
        assertEquals("CNY", resp.stats().currency());
    }

    @Test
    void getItineraryExportAssets_returnsAllDaysWithDestinationName() {
        when(tripMapper.selectById("trip-1")).thenReturn(tripWithDestination("京都"));
        when(itineraryService.getItinerary("trip-1")).thenReturn(sampleItinerary());

        ItineraryExportAssetsResp resp = exportService.getItineraryExportAssets("trip-1", null);

        assertEquals("trip-1", resp.trip().id());
        assertEquals("京都五日旅行", resp.trip().title());
        assertEquals("京都", resp.trip().destination());
        assertEquals("2026-05-01", resp.trip().startDate());
        assertEquals("2026-05-05", resp.trip().endDate());
        assertEquals(5, resp.trip().totalDays());

        assertEquals(2, resp.days().size());
        assertEquals("2026-05-01", resp.days().get(0).date());
        assertEquals(1, resp.days().get(0).nodeCount());
        assertEquals("清水寺", resp.days().get(0).nodes().getFirst().title());
        assertEquals("景点", resp.days().get(0).nodes().getFirst().typeName());
        assertEquals("09:00", resp.days().get(0).nodes().getFirst().startTime());
        assertEquals("清水寺", resp.days().get(0).nodes().getFirst().locationName());
        assertEquals("京都府京都市东山区", resp.days().get(0).nodes().getFirst().locationAddress());

        assertEquals(0, resp.days().get(1).nodeCount());
        assertTrue(resp.days().get(1).nodes().isEmpty());

        assertEquals(2, resp.meta().totalDayCount());
        assertEquals(1, resp.meta().totalNodeCount());
        assertEquals(2, resp.meta().selectedDayCount());
        assertEquals(1, resp.meta().selectedNodeCount());

        assertTrue(resp.watermark().enabled());
        assertEquals("by Tracci", resp.watermark().text());
    }

    @Test
    void getItineraryExportAssets_fallsBackToTitleWhenDestinationMissing() {
        when(tripMapper.selectById("trip-1")).thenReturn(tripWithoutDestination());
        when(itineraryService.getItinerary("trip-1")).thenReturn(sampleItinerary());

        ItineraryExportAssetsResp resp = exportService.getItineraryExportAssets("trip-1", null);

        assertEquals("京都五日旅行", resp.trip().destination());
    }

    @Test
    void getItineraryExportAssets_filtersSelectedDatesInMeta() {
        when(tripMapper.selectById("trip-1")).thenReturn(tripWithDestination("京都"));
        when(itineraryService.getItinerary("trip-1")).thenReturn(sampleItinerary());

        ItineraryExportAssetsResp resp = exportService.getItineraryExportAssets(
                "trip-1", "2026-05-01");

        assertEquals(2, resp.days().size());
        assertEquals(2, resp.meta().totalDayCount());
        assertEquals(1, resp.meta().totalNodeCount());
        assertEquals(1, resp.meta().selectedDayCount());
        assertEquals(1, resp.meta().selectedNodeCount());
    }

    @Test
    void getItineraryExportAssets_rejectsInvalidDateParam() {
        when(tripMapper.selectById("trip-1")).thenReturn(tripWithDestination("京都"));
        when(itineraryService.getItinerary("trip-1")).thenReturn(sampleItinerary());

        BizException ex = assertThrows(BizException.class,
                () -> exportService.getItineraryExportAssets("trip-1", "2026/05/01"));
        assertTrue(ex.getMessage().contains("无效的日期参数"));
    }

    @Test
    void getItineraryExportAssets_propagatesForbiddenWhenTripNotOwned() {
        Trip trip = tripWithDestination("京都");
        trip.setUserId("other-user");
        when(tripMapper.selectById("trip-1")).thenReturn(trip);

        assertThrows(ForbiddenException.class,
                () -> exportService.getItineraryExportAssets("trip-1", null));
    }

    @Test
    void getItineraryExportAssets_throwsNotFoundWhenTripMissing() {
        when(tripMapper.selectById("trip-1")).thenReturn(null);

        assertThrows(NotFoundException.class,
                () -> exportService.getItineraryExportAssets("trip-1", null));
    }

    @Test
    void getItineraryExportAssets_propagatesAuthException() {
        LoginUserContext.clear();

        assertThrows(AuthException.class,
                () -> exportService.getItineraryExportAssets("trip-1", null));
    }

    // ---- helpers ----

    private SummaryResp summaryWithFullData() {
        return new SummaryResp(
                tripInfo("trip-1", "京都五日旅行", 5, true, "https://cdn.example.com/cover.jpg"),
                stats(12, 8, 24, 34, "5860.50", "CNY", 5, 3),
                expenseSummary("5860.50", "CNY", List.of(
                        categoryStat("food", "餐饮", "2100.00", "35.83", 12),
                        categoryStat("transport", "交通", "1800.00", "30.71", 8),
                        categoryStat("hotel", "住宿", "1200.00", "20.48", 6),
                        categoryStat("attraction", "景点", "500.00", "8.53", 5),
                        categoryStat("shopping", "购物", "260.50", "4.45", 3)
                )),
                List.of(),
                List.of(
                        photoItem("file-2", "journal-2", "https://cdn.example.com/photo-2.jpg", 1),
                        photoItem("file-1", "journal-1", "https://cdn.example.com/photo-1.jpg", 1),
                        photoItem("file-3", "journal-3", "https://cdn.example.com/photo-3.jpg", 1),
                        photoItem("file-4", "journal-4", "https://cdn.example.com/photo-4.jpg", 2)
                ),
                List.of()
        );
    }

    private SummaryResp summaryWithManyPhotos() {
        return new SummaryResp(
                tripInfo("trip-1", "test", 5, false, null),
                stats(0, 0, 9, 0, "0.00", "CNY", 5, 0),
                expenseSummary("0.00", "CNY", List.of()),
                List.of(),
                List.of(
                        photoItem("fa", "ja", "url-a", 1),
                        photoItem("fb", "jb", "url-b", 1),
                        photoItem("fc", "jc", "url-c", 1),
                        photoItem("fd", "jd", "url-d", 2),
                        photoItem("fe", "je", "url-e", 1)
                ),
                List.of()
        );
    }

    private SummaryResp summaryWithEmptyPhotos() {
        return new SummaryResp(
                tripInfo("trip-1", "test", 5, false, null),
                stats(0, 0, 0, 0, "0.00", "CNY", 5, 0),
                expenseSummary("0.00", "CNY", List.of()),
                List.of(),
                List.of(),
                List.of()
        );
    }

    private SummaryResp summaryWithTwoPhotos() {
        return new SummaryResp(
                tripInfo("trip-1", "test", 5, false, null),
                stats(0, 0, 2, 0, "0.00", "CNY", 5, 0),
                expenseSummary("0.00", "CNY", List.of()),
                List.of(),
                List.of(
                        photoItem("fa", "ja", "url-a", 1),
                        photoItem("fb", "jb", "url-b", 1)
                ),
                List.of()
        );
    }

    private SummaryResp summaryWithManyCategories() {
        return new SummaryResp(
                tripInfo("trip-1", "test", 5, false, null),
                stats(0, 0, 0, 0, "8560.00", "CNY", 5, 0),
                expenseSummary("8560.00", "CNY", List.of(
                        categoryStat("hotel", "住宿", "3000.00", "35.05", 5),
                        categoryStat("food", "餐饮", "2100.00", "24.53", 12),
                        categoryStat("transport", "交通", "1800.00", "21.03", 8),
                        categoryStat("attraction", "景点", "860.00", "10.05", 6)
                )),
                List.of(),
                List.of(),
                List.of()
        );
    }

    private SummaryResp summaryWithNoExpenses() {
        return new SummaryResp(
                tripInfo("trip-1", "test", 5, false, null),
                stats(0, 0, 0, 0, "0.00", "CNY", 5, 0),
                new SummaryResp.ExpenseSummary("0.00", "CNY", List.of()),
                List.of(),
                List.of(),
                List.of()
        );
    }

    private SummaryResp summaryWithZeroDays() {
        return new SummaryResp(
                tripInfo("trip-1", "test", 0, false, null),
                stats(0, 0, 0, 10, "5000.00", "CNY", 0, 0),
                expenseSummary("5000.00", "CNY", List.of(
                        categoryStat("food", "餐饮", "5000.00", "100.00", 10)
                )),
                List.of(),
                List.of(),
                List.of()
        );
    }

    private SummaryResp summaryWithCustomAmount(String amount, long dayCount) {
        return new SummaryResp(
                tripInfo("trip-1", "test", dayCount, false, null),
                stats(0, 0, 0, 10, amount, "CNY", dayCount, 0),
                expenseSummary(amount, "CNY", List.of()),
                List.of(),
                List.of(),
                List.of()
        );
    }

    // ---- builder methods ----

    private SummaryResp.TripInfo tripInfo(String id, String title, long dayCount,
                                          boolean hasCover, String coverUrl) {
        SummaryResp.CoverPhoto cover = hasCover
                ? new SummaryResp.CoverPhoto("cover-1", coverUrl, coverUrl, coverUrl, coverUrl, coverUrl, coverUrl, coverUrl, null, 1200, 800)
                : null;
        return new SummaryResp.TripInfo(
                id, title, title,
                "2026-05-01", "2026-05-05",
                dayCount, "completed", cover);
    }

    private SummaryResp.Stats stats(long itinerary, long journal, long photo,
                                     long expense, String amount, String currency,
                                     long dayCount, long highlight) {
        return new SummaryResp.Stats(
                dayCount, itinerary, journal, photo, expense, amount, currency, highlight);
    }

    private SummaryResp.ExpenseSummary expenseSummary(String total, String currency,
                                                       List<SummaryResp.ExpenseCategoryStat> stats) {
        return new SummaryResp.ExpenseSummary(total, currency, stats);
    }

    private SummaryResp.ExpenseCategoryStat categoryStat(String cat, String name,
                                                          String amount, String pct, long count) {
        return new SummaryResp.ExpenseCategoryStat(cat, name, amount, pct, count);
    }

    private SummaryResp.PhotoItem photoItem(String fileId, String journalId,
                                             String url, int sortOrder) {
        return new SummaryResp.PhotoItem(fileId, journalId, url, url, url, url, url, url, url, null, 1080, 1440, sortOrder);
    }

    private Trip tripWithDestination(String destinationName) {
        Trip trip = new Trip();
        trip.setId("trip-1");
        trip.setUserId("user-1");
        trip.setTitle("京都五日旅行");
        trip.setDestinationName(destinationName);
        trip.setStartDate(LocalDate.of(2026, 5, 1));
        trip.setEndDate(LocalDate.of(2026, 5, 5));
        return trip;
    }

    private Trip tripWithoutDestination() {
        Trip trip = tripWithDestination("京都");
        trip.setDestinationName(null);
        return trip;
    }

    private ItineraryResp sampleItinerary() {
        return new ItineraryResp(
                "trip-1",
                List.of(
                        new ItineraryDayResp(
                                "day-1",
                                1,
                                "2026-05-01",
                                List.of(new ItineraryNodeBriefResp(
                                        "node-1",
                                        "清水寺",
                                        ItineraryNodeType.ATTRACTION.value(),
                                        "09:00",
                                        "11:00",
                                        null,
                                        new ItineraryNodeLocationResp(
                                                "poi-1",
                                                "清水寺",
                                                "京都府京都市东山区",
                                                null,
                                                null,
                                                null,
                                                null,
                                                null,
                                                null,
                                                null,
                                                "AMAP",
                                                "poi-1",
                                                "日本",
                                                null),
                                        null,
                                        null,
                                        1000))),
                        new ItineraryDayResp(
                                "day-2",
                                2,
                                "2026-05-02",
                                List.of())));
    }
}
