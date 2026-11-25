package com.tracci.module.trip.service;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tracci.common.context.LoginUserContext;
import com.tracci.common.exception.BizException;
import com.tracci.common.exception.ForbiddenException;
import com.tracci.common.response.ErrorCode;
import com.tracci.integration.cos.CiUrlBuilder;
import com.tracci.integration.cos.FileUrlResolver;
import com.tracci.integration.wechat.WechatContentSecurityClient;
import com.tracci.module.auth.entity.UserAuthIdentity;
import com.tracci.module.auth.enums.AuthProvider;
import com.tracci.module.auth.repository.UserAuthIdentityMapper;
import com.tracci.module.expense.repository.ExpenseMapper;
import com.tracci.module.file.entity.File;
import com.tracci.module.file.repository.FileMapper;
import com.tracci.module.journal.repository.JournalEntryMapper;
import com.tracci.module.journal.repository.JournalPhotoMapper;
import com.tracci.module.trip.dto.request.TripCreateReq;
import com.tracci.module.trip.dto.request.TripDestinationReq;
import com.tracci.module.trip.dto.request.TripUpdateReq;
import com.tracci.module.trip.entity.Trip;
import com.tracci.module.trip.entity.TripDay;
import com.tracci.module.trip.entity.TripMember;
import com.tracci.module.trip.enums.TripPermission;
import com.tracci.module.trip.repository.TripDayMapper;
import com.tracci.module.trip.repository.ItineraryNodeMapper;
import com.tracci.module.trip.repository.TripMapper;
import com.tracci.module.trip.repository.TripMemberMapper;
import com.tracci.module.trip.support.TripDestinationCoverProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import com.tracci.common.util.DateTimeUtil;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
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
class TripServiceImplTest {

    @Mock
    private TripMapper tripMapper;

    @Mock
    private TripDayMapper tripDayMapper;

    @Mock
    private TripMemberMapper tripMemberMapper;

    @Mock
    private TripAccessService tripAccessService;

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
    private UserAuthIdentityMapper authIdentityMapper;

    @Mock
    private WechatContentSecurityClient wechatContentSecurityClient;

    @Mock
    private TripActivityLogService activityLogService;

    @Mock
    private ItineraryService itineraryService;

    @Mock
    private com.tracci.module.savedplace.service.SavedPlaceService savedPlaceService;

    @Mock
    private com.tracci.module.place.service.PlaceService placeService;

    @InjectMocks
    private TripServiceImpl tripService;

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
        lenient().when(placeService.resolveOrCreatePlace(any(), anyString(), anyString())).thenAnswer(inv -> {
            var place = new com.tracci.module.place.entity.Place();
            place.setId("place-resolved");
            return place;
        });
    }

    @Test
    void createTrip_createsTripDaysAndOwnerMember() {
        when(tripDayMapper.restoreOrUpdateDay(eq("trip-1"), eq("u-1"), anyInt(), any(LocalDate.class)))
                .thenReturn(0);
        doAnswer(invocation -> {
            Trip trip = invocation.getArgument(0);
            trip.setId("trip-1");
            trip.setCreatedAt(LocalDateTime.of(2026, 5, 13, 10, 0));
            return 1;
        }).when(tripMapper).insert(any(Trip.class));

        LoginUserContext.setUserId("u-1");
        try {
            var resp = tripService.createTrip(new TripCreateReq(
                    " 东京 5 日 ",
                    LocalDate.of(2026, 7, 1),
                    LocalDate.of(2026, 7, 3),
                    null,
                    null,
                    null,
                    null
            ));

            assertEquals("trip-1", resp.id());
            assertEquals("东京 5 日", resp.title());
            assertEquals(3, resp.totalDays());
            assertEquals("CNY", resp.baseCurrency());
        } finally {
            LoginUserContext.clear();
        }

        ArgumentCaptor<TripDay> dayCaptor = ArgumentCaptor.forClass(TripDay.class);
        verify(tripDayMapper, times(3)).insert(dayCaptor.capture());
        assertEquals(1, dayCaptor.getAllValues().getFirst().getDayIndex());
        assertEquals(LocalDate.of(2026, 7, 3), dayCaptor.getAllValues().get(2).getDate());

        ArgumentCaptor<TripMember> memberCaptor = ArgumentCaptor.forClass(TripMember.class);
        verify(tripMemberMapper).insert(memberCaptor.capture());
        assertEquals("owner", memberCaptor.getValue().getRole());
        assertEquals("u-1", memberCaptor.getValue().getUserId());
    }

    @Test
    void createTrip_rejectsEndDateBeforeStartDate() {
        LoginUserContext.setUserId("u-1");
        try {
            assertThrows(BizException.class, () -> tripService.createTrip(new TripCreateReq(
                    "东京",
                    LocalDate.of(2026, 7, 2),
                    LocalDate.of(2026, 7, 1),
                    null,
                    "JPY",
                    null,
                    null
            )));
            verify(tripMapper, never()).insert(any(Trip.class));
        } finally {
            LoginUserContext.clear();
        }
    }

    @Test
    void createTrip_rejectsWechatMiniRiskyContentBeforeInsert() {
        UserAuthIdentity identity = new UserAuthIdentity();
        identity.setProvider(AuthProvider.WECHAT_MINI.name());
        identity.setProviderUserId("openid-1");
        when(authIdentityMapper.selectOne(any())).thenReturn(identity);
        doThrow(new BizException(ErrorCode.CONTENT_SECURITY_REJECTED))
                .when(wechatContentSecurityClient).checkText(eq("openid-1"), anyString());

        LoginUserContext.setUserId("u-1");
        try {
            BizException ex = assertThrows(BizException.class, () -> tripService.createTrip(new TripCreateReq(
                    "风险旅行",
                    LocalDate.of(2026, 7, 1),
                    LocalDate.of(2026, 7, 3),
                    null,
                    null,
                    null,
                    null
            )));
            assertEquals(ErrorCode.CONTENT_SECURITY_REJECTED, ex.getErrorCode());
            verify(tripMapper, never()).insert(any(Trip.class));
        } finally {
            LoginUserContext.clear();
        }
    }

    @Test
    void createTrip_acceptsUploadedTripCoverFileAndBindsIt() {
        File coverFile = uploadedCoverFile("file-cover-1", null);
        when(fileMapper.selectById("file-cover-1")).thenReturn(coverFile);
        when(tripDayMapper.restoreOrUpdateDay(eq("trip-1"), eq("u-1"), anyInt(), any(LocalDate.class)))
                .thenReturn(0);
        doAnswer(invocation -> {
            Trip trip = invocation.getArgument(0);
            trip.setId("trip-1");
            trip.setCreatedAt(LocalDateTime.of(2026, 5, 13, 10, 0));
            return 1;
        }).when(tripMapper).insert(any(Trip.class));

        LoginUserContext.setUserId("u-1");
        try {
            var resp = tripService.createTrip(new TripCreateReq(
                    "京都",
                    LocalDate.of(2026, 7, 1),
                    LocalDate.of(2026, 7, 1),
                    "file-cover-1",
                    "CNY",
                    null,
                    null
            ));

            assertEquals("file-cover-1", resp.coverPhoto().fileId());
            assertEquals("https://cdn.example.com/cover.jpg", resp.coverPhoto().url());
        } finally {
            LoginUserContext.clear();
        }

        ArgumentCaptor<Trip> tripCaptor = ArgumentCaptor.forClass(Trip.class);
        verify(tripMapper).insert(tripCaptor.capture());
        assertEquals("file-cover-1", tripCaptor.getValue().getCoverFileId());
        verify(fileMapper).updateById(coverFile);
        assertEquals("trip-1", coverFile.getBusinessId());
    }

    @Test
    void updateTrip_rejectsPendingCoverFile() {
        Trip trip = trip("trip-1", "u-1");
        File coverFile = uploadedCoverFile("file-cover-1", "trip-1");
        coverFile.setStatus("PENDING");
        when(tripMapper.selectById("trip-1")).thenReturn(trip);
        when(fileMapper.selectById("file-cover-1")).thenReturn(coverFile);

        LoginUserContext.setUserId("u-1");
        try {
            assertThrows(BizException.class, () -> tripService.updateTrip("trip-1", new TripUpdateReq(
                    null,
                    null,
                    null,
                    "file-cover-1",
                    null,
                    null,
                    null
            )));
            verify(tripMapper, never()).update(isNull(), any(UpdateWrapper.class));
        } finally {
            LoginUserContext.clear();
        }
    }

    @Test
    void getTrip_rejectsOtherUserTrip() {
        Trip trip = new Trip();
        trip.setId("trip-1");
        trip.setUserId("u-2");
        when(tripMapper.selectById("trip-1")).thenReturn(trip);

        LoginUserContext.setUserId("u-1");
        try {
            assertThrows(ForbiddenException.class, () -> tripService.getTrip("trip-1"));
        } finally {
            LoginUserContext.clear();
        }
    }

    @Test
    void getTrip_returnsJournalCount() {
        Trip trip = trip("trip-1", "u-1");
        TripMember owner = new TripMember();
        owner.setId("member-owner");
        owner.setTripId("trip-1");
        owner.setUserId("u-1");
        owner.setRole("owner");
        owner.setStatus("active");
        doReturn(new TripAccessService.TripAccess(trip, owner))
                .when(tripAccessService)
                .requirePermission("trip-1", "u-1", TripPermission.READ);
        when(expenseMapper.sumVisibleAmountCnyByTripIdAndUserId("trip-1", "u-1")).thenReturn(new BigDecimal("3200.00"));
        when(itineraryNodeMapper.countByTripIdAndUserId("trip-1", "u-1")).thenReturn(12L);
        when(journalEntryMapper.countVisibleByTripIdAndUserId("trip-1", "u-1")).thenReturn(3L);

        LoginUserContext.setUserId("u-1");
        try {
            var resp = tripService.getTrip("trip-1");

            assertEquals("3200.00", resp.totalCny());
            assertEquals(12L, resp.nodeCount());
            assertEquals(3L, resp.journalCount());
            assertEquals("owner", resp.access().role());
        } finally {
            LoginUserContext.clear();
        }
    }

    @Test
    void getTrip_returnsCurrentMemberRoleFromAccessContext() {
        Trip trip = trip("trip-1", "owner-1");
        TripMember editor = new TripMember();
        editor.setId("member-editor");
        editor.setTripId("trip-1");
        editor.setUserId("editor-1");
        editor.setRole("editor");
        editor.setStatus("active");
        doReturn(new TripAccessService.TripAccess(trip, editor))
                .when(tripAccessService)
                .requirePermission("trip-1", "editor-1", TripPermission.READ);

        LoginUserContext.setUserId("editor-1");
        try {
            var resp = tripService.getTrip("trip-1");

            assertEquals("editor", resp.access().role());
        } finally {
            LoginUserContext.clear();
        }
    }

    @Test
    void listTrips_returnsJournalCount() {
        Trip trip = trip("trip-1", "u-1");
        Page<Trip> page = new Page<>(1, 20);
        page.setRecords(List.of(trip));
        page.setTotal(1);
        when(tripMapper.selectPage(any(Page.class), any())).thenReturn(page);
        List<String> tripIds = List.of("trip-1");
        when(expenseMapper.sumVisibleAmountCnyByTripIdsAndUserId(tripIds, "u-1")).thenReturn(List.of(
                new ExpenseMapper.TripAmountSummary("trip-1", new BigDecimal("800.00"))
        ));
        when(itineraryNodeMapper.countByTripIdsAndUserId(tripIds, "u-1")).thenReturn(List.of(
                new ItineraryNodeMapper.TripCountSummary("trip-1", 5L)
        ));
        when(journalEntryMapper.countVisibleGroupByTripIdsAndUserId(tripIds, "u-1")).thenReturn(List.of(
                new JournalEntryMapper.TripCountSummary("trip-1", 2L)
        ));

        LoginUserContext.setUserId("u-1");
        try {
            var resp = tripService.listTrips(1, 20, null, null);

            assertEquals("800.00", resp.list().getFirst().totalCny());
            assertEquals(5L, resp.list().getFirst().nodeCount());
            assertEquals(2L, resp.list().getFirst().journalCount());
        } finally {
            LoginUserContext.clear();
        }

        verify(expenseMapper, never()).sumVisibleAmountCnyByTripIdAndUserId("trip-1", "u-1");
        verify(itineraryNodeMapper, never()).countByTripIdAndUserId("trip-1", "u-1");
        verify(journalEntryMapper, never()).countVisibleByTripIdAndUserId("trip-1", "u-1");
    }

    @Test
    void listTrips_usesMemberMapperWithDateBasedStatusFilter() {
        Page<Trip> page = new Page<>(1, 20);
        page.setRecords(List.of());
        page.setTotal(0);
        when(tripMapper.pageMemberTrips(any(Page.class), eq("u-1"), eq("ongoing"), eq("东京"), any(LocalDate.class)))
                .thenReturn(page);

        LoginUserContext.setUserId("u-1");
        try {
            var resp = tripService.listTrips(1, 20, "ongoing", " 东京 ");

            assertEquals(0, resp.total());
        } finally {
            LoginUserContext.clear();
        }

        ArgumentCaptor<LocalDate> todayCaptor = ArgumentCaptor.forClass(LocalDate.class);
        verify(tripMapper).pageMemberTrips(any(Page.class), eq("u-1"), eq("ongoing"), eq("东京"), todayCaptor.capture());
        assertEquals(DateTimeUtil.todayBusiness(), todayCaptor.getValue());
        verify(tripMapper, never()).selectPage(any(Page.class), any());
    }

    @Test
    void listTrips_returnsRealtimeStatusWhenStoredStatusIsStale() {
        Trip trip = trip("trip-1", "u-1");
        trip.setStartDate(DateTimeUtil.todayBusiness().minusDays(1));
        trip.setEndDate(DateTimeUtil.todayBusiness().plusDays(1));
        trip.setStatus("planning");
        Page<Trip> page = new Page<>(1, 20);
        page.setRecords(List.of(trip));
        page.setTotal(1);
        when(tripMapper.pageMemberTrips(any(Page.class), eq("u-1"), isNull(), isNull(), any(LocalDate.class)))
                .thenReturn(page);
        List<String> tripIds = List.of("trip-1");
        when(expenseMapper.sumVisibleAmountCnyByTripIdsAndUserId(tripIds, "u-1")).thenReturn(List.of());
        when(itineraryNodeMapper.countGroupByTripIds(tripIds)).thenReturn(List.of());
        when(itineraryNodeMapper.countByTripIdsAndUserId(tripIds, "u-1")).thenReturn(List.of());
        when(journalEntryMapper.countVisibleGroupByTripIdsAndUserId(tripIds, "u-1")).thenReturn(List.of());

        LoginUserContext.setUserId("u-1");
        try {
            var resp = tripService.listTrips(1, 20, null, null);

            assertEquals("ongoing", resp.list().getFirst().status());
        } finally {
            LoginUserContext.clear();
        }
    }

    @Test
    void updateTrip_syncsTripDaysWhenDateRangeShrinks() {
        Trip trip = trip("trip-1", "u-1");
        trip.setBaseCurrency("JPY");
        when(tripMapper.selectById("trip-1")).thenReturn(trip);
        when(tripMapper.update(isNull(), any(UpdateWrapper.class))).thenReturn(1);
        when(tripDayMapper.restoreOrUpdateDay(eq("trip-1"), eq("u-1"), anyInt(), any(LocalDate.class)))
                .thenReturn(1);
        when(journalEntryMapper.countVisibleByTripIdAndUserId("trip-1", "u-1")).thenReturn(4L);

        LoginUserContext.setUserId("u-1");
        try {
            var resp = tripService.updateTrip("trip-1", new TripUpdateReq(
                    null,
                    null,
                    LocalDate.of(2026, 7, 2),
                    null,
                    null,
                    null,
                    null
            ));
            assertEquals(2, resp.totalDays());
            assertEquals("2026-07-02", resp.endDate());
            assertEquals(4L, resp.journalCount());
        } finally {
            LoginUserContext.clear();
        }

        verify(tripMapper).update(isNull(), any(UpdateWrapper.class));
        verify(tripDayMapper).softDeleteAfterDayIndex("trip-1", "u-1", 2);
    }

    // ---- 状态变更守卫：已结束旅程改日期需确认（日期相对 DateTimeUtil.todayBusiness()，避免单测过期） ----

    @Test
    void updateTrip_completedTripDateChangeToFuture_requiresConfirmation() {
        LocalDate today = DateTimeUtil.todayBusiness();
        Trip trip = trip("trip-1", "u-1");
        trip.setStartDate(today.minusDays(10));
        trip.setEndDate(today.minusDays(5));
        when(tripMapper.selectById("trip-1")).thenReturn(trip);

        LoginUserContext.setUserId("u-1");
        try {
            BizException ex = assertThrows(BizException.class, () -> tripService.updateTrip("trip-1", new TripUpdateReq(
                    null,
                    null,
                    today.plusDays(5), // 改到未来 → 会变为 ONGOING
                    null,
                    null,
                    null,
                    null
            )));
            assertEquals(ErrorCode.TRIP_STATUS_CHANGE_REQUIRES_CONFIRM, ex.getErrorCode());
            verify(tripMapper, never()).update(isNull(), any(UpdateWrapper.class));
        } finally {
            LoginUserContext.clear();
        }
    }

    @Test
    void updateTrip_completedTripDateChangeWithConfirmation_succeeds() {
        LocalDate today = DateTimeUtil.todayBusiness();
        Trip trip = trip("trip-1", "u-1");
        trip.setStartDate(today.minusDays(10));
        trip.setEndDate(today.minusDays(5));
        when(tripMapper.selectById("trip-1")).thenReturn(trip);
        when(tripMapper.update(isNull(), any(UpdateWrapper.class))).thenReturn(1);
        when(tripDayMapper.restoreOrUpdateDay(eq("trip-1"), eq("u-1"), anyInt(), any(LocalDate.class)))
                .thenReturn(1);
        when(journalEntryMapper.countVisibleByTripIdAndUserId("trip-1", "u-1")).thenReturn(0L);

        LocalDate newEnd = today.plusDays(5);
        LoginUserContext.setUserId("u-1");
        try {
            var resp = tripService.updateTrip("trip-1", new TripUpdateReq(
                    null,
                    null,
                    newEnd,
                    null,
                    null,
                    null,
                    true // 已确认
            ));
            assertEquals(newEnd.toString(), resp.endDate());
            assertEquals("ongoing", resp.status());
        } finally {
            LoginUserContext.clear();
        }
    }

    @Test
    void updateTrip_completedTripDateChangeThatStaysCompleted_succeedsWithoutConfirmation() {
        LocalDate today = DateTimeUtil.todayBusiness();
        Trip trip = trip("trip-1", "u-1");
        trip.setStartDate(today.minusDays(10));
        trip.setEndDate(today.minusDays(5));
        when(tripMapper.selectById("trip-1")).thenReturn(trip);
        when(tripMapper.update(isNull(), any(UpdateWrapper.class))).thenReturn(1);
        when(tripDayMapper.restoreOrUpdateDay(eq("trip-1"), eq("u-1"), anyInt(), any(LocalDate.class)))
                .thenReturn(1);
        when(journalEntryMapper.countVisibleByTripIdAndUserId("trip-1", "u-1")).thenReturn(0L);

        LocalDate newStart = today.minusDays(20);
        LoginUserContext.setUserId("u-1");
        try {
            // 只把开始日期提前，结束日期不变（仍在过去）→ 仍为 COMPLETED，无需确认
            var resp = tripService.updateTrip("trip-1", new TripUpdateReq(
                    null,
                    newStart,
                    null,
                    null,
                    null,
                    null,
                    null
            ));
            assertEquals(newStart.toString(), resp.startDate());
            assertEquals("completed", resp.status());
        } finally {
            LoginUserContext.clear();
        }
    }

    @Test
    void updateTrip_ongoingTripDateChange_succeedsWithoutConfirmation() {
        LocalDate today = DateTimeUtil.todayBusiness();
        Trip trip = trip("trip-1", "u-1");
        trip.setStartDate(today.minusDays(2));
        trip.setEndDate(today.plusDays(3));
        when(tripMapper.selectById("trip-1")).thenReturn(trip);
        when(tripMapper.update(isNull(), any(UpdateWrapper.class))).thenReturn(1);
        when(tripDayMapper.restoreOrUpdateDay(eq("trip-1"), eq("u-1"), anyInt(), any(LocalDate.class)))
                .thenReturn(1);
        when(journalEntryMapper.countVisibleByTripIdAndUserId("trip-1", "u-1")).thenReturn(0L);

        LocalDate newEnd = today.plusDays(10);
        LoginUserContext.setUserId("u-1");
        try {
            var resp = tripService.updateTrip("trip-1", new TripUpdateReq(
                    null,
                    null,
                    newEnd,
                    null,
                    null,
                    null,
                    null
            ));
            assertEquals(newEnd.toString(), resp.endDate());
            assertEquals("ongoing", resp.status());
        } finally {
            LoginUserContext.clear();
        }
    }

    @Test
    void createTrip_savesCityLevelDestination() {
        when(tripDayMapper.restoreOrUpdateDay(eq("trip-1"), eq("u-1"), anyInt(), any(LocalDate.class)))
                .thenReturn(0);
        doAnswer(invocation -> {
            Trip trip = invocation.getArgument(0);
            trip.setId("trip-1");
            trip.setCreatedAt(LocalDateTime.of(2026, 5, 13, 10, 0));
            return 1;
        }).when(tripMapper).insert(any(Trip.class));

        TripDestinationReq destination = new TripDestinationReq(
                "杭州",
                "330106",
                "0571",
                "浙江省",
                "杭州市",
                "西湖区",
                new BigDecimal("30.2420000"),
                new BigDecimal("120.1410000"),
                "AMAP",
                null,
                "中国"
        );

        LoginUserContext.setUserId("u-1");
        try {
            var resp = tripService.createTrip(new TripCreateReq(
                    "杭州周末",
                    LocalDate.of(2026, 7, 1),
                    LocalDate.of(2026, 7, 1),
                    null,
                    "CNY",
                    destination,
                    null
            ));

            assertEquals("杭州", resp.destination().name());
            assertEquals("杭州市", resp.destination().cityName());
            assertEquals(new BigDecimal("30.2420000"), resp.destination().latitude());
            assertEquals("place-resolved", resp.destination().placeId());
        } finally {
            LoginUserContext.clear();
        }

        ArgumentCaptor<Trip> tripCaptor = ArgumentCaptor.forClass(Trip.class);
        verify(tripMapper).insert(tripCaptor.capture());
        assertEquals("330106", tripCaptor.getValue().getDestinationAdcode());
        assertEquals(new BigDecimal("30.2420000"), tripCaptor.getValue().getDestinationLatitude());
        assertEquals("place-resolved", tripCaptor.getValue().getDestinationPlaceId());
        verify(placeService).resolveOrCreatePlace(any(), eq("DESTINATION"), eq("CITY"));
    }

    @Test
    void createTrip_fromDestinationSavedPlace_appliesPlaceAndRecordsUsage() {
        when(tripDayMapper.restoreOrUpdateDay(eq("trip-1"), eq("u-1"), anyInt(), any(LocalDate.class)))
                .thenReturn(0);
        doAnswer(invocation -> {
            Trip trip = invocation.getArgument(0);
            trip.setId("trip-1");
            trip.setCreatedAt(LocalDateTime.of(2026, 5, 13, 10, 0));
            return 1;
        }).when(tripMapper).insert(any(Trip.class));

        com.tracci.module.place.entity.Place place = new com.tracci.module.place.entity.Place();
        place.setId("place-tokyo");
        place.setCanonicalName("东京");
        place.setCityName("东京");
        place.setCountry("日本");
        place.setDestinationKey("adcode:131000");
        place.setLatitude(new BigDecimal("35.68"));
        place.setLongitude(new BigDecimal("139.76"));
        when(savedPlaceService.resolveForTripDestination("u-1", "sp-dest")).thenReturn(place);

        LoginUserContext.setUserId("u-1");
        try {
            var resp = tripService.createTrip(new TripCreateReq(
                    "东京行",
                    LocalDate.of(2026, 8, 1),
                    LocalDate.of(2026, 8, 5),
                    null,
                    "JPY",
                    null,
                    "sp-dest"
            ));

            assertEquals("东京", resp.destination().name());
            assertEquals("place-tokyo", resp.destination().placeId());
            assertEquals("131000", resp.destination().adcode());
            verify(savedPlaceService).recordTripUsage("u-1", "sp-dest", "trip-1");
        } finally {
            LoginUserContext.clear();
        }
    }

    @Test
    void updateTrip_updatesCityLevelDestination() {
        Trip trip = trip("trip-1", "u-1");
        when(tripMapper.selectById("trip-1")).thenReturn(trip);
        when(tripMapper.update(isNull(), any(UpdateWrapper.class))).thenReturn(1);

        TripDestinationReq destination = new TripDestinationReq(
                "京都",
                "26105",
                "075",
                "京都府",
                "京都市",
                "东山区",
                new BigDecimal("35.0116000"),
                new BigDecimal("135.7681000"),
                "APPLE",
                "apple-place-kyoto",
                "日本"
        );

        LoginUserContext.setUserId("u-1");
        try {
            var resp = tripService.updateTrip("trip-1", new TripUpdateReq(
                    null,
                    null,
                    null,
                    null,
                    null,
                    destination,
                    null
            ));

            assertEquals("京都", resp.destination().name());
            assertEquals("京都市", resp.destination().cityName());
        } finally {
            LoginUserContext.clear();
        }

        ArgumentCaptor<UpdateWrapper<Trip>> updateCaptor = ArgumentCaptor.forClass(UpdateWrapper.class);
        verify(tripMapper).update(isNull(), updateCaptor.capture());
        assertEquals("京都", trip.getDestinationName());
        assertEquals("京都市", trip.getDestinationCityName());
    }

    @Test
    void deleteTrip_softDeletesChildDataAndTripFiles() {
        Trip trip = trip("trip-1", "u-1");
        when(tripMapper.selectById("trip-1")).thenReturn(trip);
        when(tripMapper.delete(any(UpdateWrapper.class))).thenReturn(1);

        LoginUserContext.setUserId("u-1");
        try {
            tripService.deleteTrip("trip-1");
        } finally {
            LoginUserContext.clear();
        }

        verify(itineraryNodeMapper).softDeleteByTripId("trip-1", "u-1");
        verify(tripDayMapper).softDeleteByTripId("trip-1", "u-1");
        verify(expenseMapper).softDeleteByTripId("trip-1", "u-1");
        verify(journalPhotoMapper).softDeleteByTripId("trip-1", "u-1");
        verify(journalEntryMapper).softDeleteByTripId("trip-1", "u-1");
        verify(fileMapper).softDeleteByBusiness("u-1", "JOURNAL_PHOTO", "trip-1");
        verify(fileMapper).softDeleteByBusiness("u-1", "TRIP_COVER", "trip-1");
    }

    private Trip trip(String tripId, String userId) {
        Trip trip = new Trip();
        trip.setId(tripId);
        trip.setUserId(userId);
        trip.setTitle("东京 5 日");
        trip.setStartDate(LocalDate.of(2026, 7, 1));
        trip.setEndDate(LocalDate.of(2026, 7, 5));
        trip.setBaseCurrency("CNY");
        trip.setCreatedAt(LocalDateTime.of(2026, 5, 13, 10, 0));
        trip.setUpdatedAt(LocalDateTime.of(2026, 5, 13, 10, 0));
        return trip;
    }

    private File uploadedCoverFile(String fileId, String businessId) {
        File file = new File();
        file.setId(fileId);
        file.setUserId("u-1");
        file.setBusinessType("TRIP_COVER");
        file.setBusinessId(businessId);
        file.setContentType("image/jpeg");
        file.setStatus("UPLOADED");
        file.setUrl("https://cdn.example.com/cover.jpg");
        file.setObjectKey("users/u-1/trip-cover/" + fileId + ".jpg");
        file.setWidth(1080);
        file.setHeight(1440);
        return file;
    }
}
