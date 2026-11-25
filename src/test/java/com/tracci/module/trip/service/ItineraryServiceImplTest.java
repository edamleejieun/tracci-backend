package com.tracci.module.trip.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.tracci.common.context.LoginUserContext;
import com.tracci.common.exception.BizException;
import com.tracci.common.exception.ForbiddenException;
import com.tracci.common.response.ErrorCode;
import com.tracci.module.trip.dto.request.ItineraryNodeCreateReq;
import com.tracci.module.trip.dto.request.ItineraryNodeLocationReq;
import com.tracci.module.trip.dto.request.ItineraryNodeReorderReq;
import com.tracci.module.trip.dto.request.ItineraryNodeUpdateReq;
import com.tracci.module.trip.entity.ItineraryNode;
import com.tracci.module.trip.entity.Trip;
import com.tracci.module.trip.entity.TripDay;
import com.tracci.module.trip.repository.ItineraryNodeMapper;
import com.tracci.module.trip.repository.TripDayMapper;
import com.tracci.module.trip.repository.TripMapper;
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
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static com.tracci.testsupport.TripAccessTestSupport.stubOwnerAccess;

@ExtendWith(MockitoExtension.class)
class ItineraryServiceImplTest {

    @Mock
    private TripMapper tripMapper;

    @Mock
    private TripDayMapper tripDayMapper;

    @Mock
    private ItineraryNodeMapper itineraryNodeMapper;

    @Mock
    private TripAccessService tripAccessService;

    @Mock
    private TripActivityLogService activityLogService;

    @Mock
    private com.tracci.module.savedplace.service.SavedPlaceService savedPlaceService;

    @Mock
    private com.tracci.module.place.service.PlaceService placeService;

    @Mock
    private com.tracci.module.expense.repository.ExpenseMapper expenseMapper;

    @InjectMocks
    private ItineraryServiceImpl itineraryService;

    @BeforeEach
    void setUpAccess() {
        stubOwnerAccess(tripAccessService, tripMapper);
        lenient().when(placeService.resolveOrCreatePlace(any(), any(), any())).thenAnswer(inv -> {
            var place = new com.tracci.module.place.entity.Place();
            place.setId("place-resolved");
            return place;
        });
    }

    @Test
    void getItinerary_groupsDaysAndNodes() {
        when(tripMapper.selectById("trip-1")).thenReturn(trip("u-1"));
        when(tripDayMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(
                day("day-1", 1),
                day("day-2", 2)
        ));
        ItineraryNode node = node("node-1", "day-1", 1000);
        when(itineraryNodeMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(node));

        LoginUserContext.setUserId("u-1");
        try {
            var resp = itineraryService.getItinerary("trip-1");

            assertEquals("trip-1", resp.tripId());
            assertEquals(2, resp.days().size());
            assertEquals("day-1", resp.days().getFirst().id());
            assertEquals(1, resp.days().getFirst().nodes().size());
            assertEquals("09:00", resp.days().getFirst().nodes().getFirst().startTime());
            assertEquals(0, resp.days().get(1).nodes().size());
        } finally {
            LoginUserContext.clear();
        }
    }

    @Test
    void createNode_setsOwnershipAndNextSortOrder() {
        when(tripMapper.selectById("trip-1")).thenReturn(trip("u-1"));
        when(tripDayMapper.selectOne(any(QueryWrapper.class))).thenReturn(day("day-1", 1));
        when(itineraryNodeMapper.selectMaxSortOrder("trip-1", "day-1", "u-1")).thenReturn(2000);
        doAnswer(invocation -> {
            ItineraryNode node = invocation.getArgument(0);
            node.setId("node-1");
            node.setCreatedAt(LocalDateTime.of(2026, 5, 13, 10, 0));
            return 1;
        }).when(itineraryNodeMapper).insert(any(ItineraryNode.class));

        LoginUserContext.setUserId("u-1");
        try {
            var resp = itineraryService.createNode("trip-1", "day-1", new ItineraryNodeCreateReq(
                    " 浅草寺 ",
                    "attraction",
                    "10:00",
                    "12:00",
                    " 东京都台东区 ",
                    " 早点去 ",
                    location("浅草寺"),
                    null,
                    null
            ));

            assertEquals("node-1", resp.id());
            assertEquals(3000, resp.sortOrder());
            assertEquals("浅草寺", resp.title());
        } finally {
            LoginUserContext.clear();
        }

        ArgumentCaptor<ItineraryNode> captor = ArgumentCaptor.forClass(ItineraryNode.class);
        verify(itineraryNodeMapper).insert(captor.capture());
        assertEquals("u-1", captor.getValue().getUserId());
        assertEquals("trip-1", captor.getValue().getTripId());
        assertEquals("day-1", captor.getValue().getTripDayId());
    }

    @Test
    void createNode_acceptsShoppingType() {
        when(tripMapper.selectById("trip-1")).thenReturn(trip("u-1"));
        when(tripDayMapper.selectOne(any(QueryWrapper.class))).thenReturn(day("day-1", 1));
        when(itineraryNodeMapper.selectMaxSortOrder("trip-1", "day-1", "u-1")).thenReturn(null);
        doAnswer(invocation -> {
            ItineraryNode node = invocation.getArgument(0);
            node.setId("node-1");
            node.setCreatedAt(LocalDateTime.of(2026, 5, 13, 10, 0));
            return 1;
        }).when(itineraryNodeMapper).insert(any(ItineraryNode.class));

        LoginUserContext.setUserId("u-1");
        try {
            var resp = itineraryService.createNode("trip-1", "day-1", new ItineraryNodeCreateReq(
                    "银座",
                    "shopping",
                    null,
                    null,
                    null,
                    null,
                    location("银座"),
                    null,
                    null
            ));

            assertEquals("shopping", resp.type());
        } finally {
            LoginUserContext.clear();
        }
    }

    @Test
    void createNode_rejectsInvalidTimeRange() {
        when(tripMapper.selectById("trip-1")).thenReturn(trip("u-1"));
        when(tripDayMapper.selectOne(any(QueryWrapper.class))).thenReturn(day("day-1", 1));

        LoginUserContext.setUserId("u-1");
        try {
            assertThrows(BizException.class, () -> itineraryService.createNode("trip-1", "day-1",
                    new ItineraryNodeCreateReq("浅草寺", "attraction", "12:00", "10:00", null, null, null, null, null)));
        } finally {
            LoginUserContext.clear();
        }
    }

    @Test
    void updateNode_rejectsOtherUserTrip() {
        when(tripMapper.selectById("trip-1")).thenReturn(trip("u-2"));

        LoginUserContext.setUserId("u-1");
        try {
            assertThrows(ForbiddenException.class, () -> itineraryService.updateNode("trip-1", "day-1", "node-1",
                    new ItineraryNodeUpdateReq("浅草寺", null, null, null, null, null, null, null)));
        } finally {
            LoginUserContext.clear();
        }
    }

    @Test
    void createNode_savesStructuredPoiLocation() {
        when(tripMapper.selectById("trip-1")).thenReturn(trip("u-1"));
        when(tripDayMapper.selectOne(any(QueryWrapper.class))).thenReturn(day("day-1", 1));
        when(itineraryNodeMapper.selectMaxSortOrder("trip-1", "day-1", "u-1")).thenReturn(null);
        doAnswer(invocation -> {
            ItineraryNode node = invocation.getArgument(0);
            node.setId("node-1");
            node.setCreatedAt(LocalDateTime.of(2026, 5, 13, 10, 0));
            return 1;
        }).when(itineraryNodeMapper).insert(any(ItineraryNode.class));

        ItineraryNodeLocationReq location = new ItineraryNodeLocationReq(
                "B023B",
                "西湖风景名胜区",
                "浙江省杭州市西湖区龙井路1号",
                new BigDecimal("30.2420000"),
                new BigDecimal("120.1410000"),
                "330106",
                "0571",
                "浙江省",
                "杭州市",
                "西湖区",
                "AMAP",
                null,
                "中国"
        );

        LoginUserContext.setUserId("u-1");
        try {
            var resp = itineraryService.createNode("trip-1", "day-1", new ItineraryNodeCreateReq(
                    "西湖",
                    "attraction",
                    "10:00",
                    "12:00",
                    null,
                    null,
                    location,
                    null,
                    null
            ));

            assertEquals("西湖风景名胜区", resp.location().name());
            assertEquals(null, resp.address());
            assertEquals("place-resolved", resp.location().placeId());
        } finally {
            LoginUserContext.clear();
        }

        ArgumentCaptor<ItineraryNode> captor = ArgumentCaptor.forClass(ItineraryNode.class);
        verify(itineraryNodeMapper).insert(captor.capture());
        assertEquals("B023B", captor.getValue().getLocationPoiId());
        assertEquals("330106", captor.getValue().getLocationAdcode());
        assertEquals(new BigDecimal("30.2420000"), captor.getValue().getLocationLatitude());
        assertEquals("place-resolved", captor.getValue().getLocationPlaceId());
        verify(placeService).resolveOrCreatePlace(any(), eq("POI"), eq("ATTRACTION"));
    }

    @Test
    void updateNode_updatesStructuredPoiLocation() {
        when(tripMapper.selectById("trip-1")).thenReturn(trip("u-1"));
        when(tripDayMapper.selectOne(any(QueryWrapper.class))).thenReturn(day("day-1", 1));
        ItineraryNode node = node("node-1", "day-1", 1000);
        when(itineraryNodeMapper.selectOne(any(QueryWrapper.class))).thenReturn(node);
        when(itineraryNodeMapper.updateById(node)).thenReturn(1);

        ItineraryNodeLocationReq location = new ItineraryNodeLocationReq(
                "poi-1",
                "清水寺",
                "京都府京都市东山区",
                new BigDecimal("34.9949000"),
                new BigDecimal("135.7850000"),
                "26105",
                "075",
                "京都府",
                "京都市",
                "东山区",
                "APPLE",
                "apple-place-1",
                "日本"
        );

        LoginUserContext.setUserId("u-1");
        try {
            var resp = itineraryService.updateNode("trip-1", "day-1", "node-1", new ItineraryNodeUpdateReq(
                    null,
                    null,
                    null,
                    null,
                    "",
                    null,
                    location,
                    null
            ));

            assertEquals("清水寺", resp.location().name());
            assertEquals(null, resp.address());
        } finally {
            LoginUserContext.clear();
        }

        assertEquals("apple-place-1", node.getLocationPoiId());
        assertEquals("APPLE", node.getLocationProvider());
        assertEquals("京都市", node.getLocationCityName());
    }

    // ---- 已结束旅程节点仅允许修改备注（日期相对 DateTimeUtil.todayBusiness()） ----

    @Test
    void updateNode_completedTripNoteOnlyUpdate_succeeds() {
        LocalDate today = DateTimeUtil.todayBusiness();
        Trip trip = tripWithDates("u-1", today.minusDays(10), today.minusDays(5));
        when(tripMapper.selectById("trip-1")).thenReturn(trip);
        when(tripDayMapper.selectOne(any(QueryWrapper.class))).thenReturn(day("day-1", 1));
        ItineraryNode node = node("node-1", "day-1", 1000);
        when(itineraryNodeMapper.selectOne(any(QueryWrapper.class))).thenReturn(node);
        when(itineraryNodeMapper.updateById(node)).thenReturn(1);

        LoginUserContext.setUserId("u-1");
        try {
            // ItineraryNodeUpdateReq 字段顺序：title, type, startTime, endTime, address, note, location
            var resp = itineraryService.updateNode("trip-1", "day-1", "node-1", new ItineraryNodeUpdateReq(
                    null,
                    null,
                    null,
                    null,
                    null,
                    "下次可以多留半天",   // 仅 note（第 6 个字段）非 null
                    null,
                    null
            ));
            assertEquals("下次可以多留半天", resp.note());
        } finally {
            LoginUserContext.clear();
        }

        // note 已更新，title 保持不变
        assertEquals("浅草寺", node.getTitle());
        verify(itineraryNodeMapper).updateById(node);
    }

    @Test
    void updateNode_completedTripTitleChange_rejects() {
        LocalDate today = DateTimeUtil.todayBusiness();
        Trip trip = tripWithDates("u-1", today.minusDays(10), today.minusDays(5));
        when(tripMapper.selectById("trip-1")).thenReturn(trip);
        when(tripDayMapper.selectOne(any(QueryWrapper.class))).thenReturn(day("day-1", 1));
        ItineraryNode node = node("node-1", "day-1", 1000);
        when(itineraryNodeMapper.selectOne(any(QueryWrapper.class))).thenReturn(node);

        LoginUserContext.setUserId("u-1");
        try {
            BizException ex = assertThrows(BizException.class, () -> itineraryService.updateNode(
                    "trip-1", "day-1", "node-1",
                    new ItineraryNodeUpdateReq("新标题", null, null, null, null, null, null, null)));
            assertEquals(ErrorCode.ITINERARY_COMPLETED_NODE_NOTE_ONLY, ex.getErrorCode());
            verify(itineraryNodeMapper, never()).updateById(any(ItineraryNode.class));
        } finally {
            LoginUserContext.clear();
        }
    }

    @Test
    void updateNode_completedTripLocationChange_rejects() {
        LocalDate today = DateTimeUtil.todayBusiness();
        Trip trip = tripWithDates("u-1", today.minusDays(10), today.minusDays(5));
        when(tripMapper.selectById("trip-1")).thenReturn(trip);
        when(tripDayMapper.selectOne(any(QueryWrapper.class))).thenReturn(day("day-1", 1));
        ItineraryNode node = node("node-1", "day-1", 1000);
        when(itineraryNodeMapper.selectOne(any(QueryWrapper.class))).thenReturn(node);

        ItineraryNodeLocationReq location = new ItineraryNodeLocationReq(
                "poi-1", "新地点", null, null, null, null, null, null, null, null, "MANUAL", null, null);

        LoginUserContext.setUserId("u-1");
        try {
            BizException ex = assertThrows(BizException.class, () -> itineraryService.updateNode(
                    "trip-1", "day-1", "node-1",
                    new ItineraryNodeUpdateReq(null, null, null, null, null, null, location, null)));
            assertEquals(ErrorCode.ITINERARY_COMPLETED_NODE_NOTE_ONLY, ex.getErrorCode());
            verify(itineraryNodeMapper, never()).updateById(any(ItineraryNode.class));
        } finally {
            LoginUserContext.clear();
        }
    }

    @Test
    void updateNode_ongoingTripFullUpdate_succeeds() {
        LocalDate today = DateTimeUtil.todayBusiness();
        Trip trip = tripWithDates("u-1", today.minusDays(2), today.plusDays(3));
        when(tripMapper.selectById("trip-1")).thenReturn(trip);
        when(tripDayMapper.selectOne(any(QueryWrapper.class))).thenReturn(day("day-1", 1));
        ItineraryNode node = node("node-1", "day-1", 1000);
        when(itineraryNodeMapper.selectOne(any(QueryWrapper.class))).thenReturn(node);
        when(itineraryNodeMapper.updateById(node)).thenReturn(1);

        LoginUserContext.setUserId("u-1");
        try {
            var resp = itineraryService.updateNode("trip-1", "day-1", "node-1", new ItineraryNodeUpdateReq(
                    "新标题",
                    null,
                    null,
                    null,
                    null,
                    "新备注",
                    null,
                    null
            ));
            assertEquals("新标题", resp.title());
            assertEquals("新备注", resp.note());
        } finally {
            LoginUserContext.clear();
        }

        assertEquals("新标题", node.getTitle());
        verify(itineraryNodeMapper).updateById(node);
    }

    @Test
    void updateNode_completedTripEmptyNote_clearsNote() {
        // 后端语义：note 非 null 即更新；"" 经 normalizeOptional 清空为 null
        LocalDate today = DateTimeUtil.todayBusiness();
        Trip trip = tripWithDates("u-1", today.minusDays(10), today.minusDays(5));
        when(tripMapper.selectById("trip-1")).thenReturn(trip);
        when(tripDayMapper.selectOne(any(QueryWrapper.class))).thenReturn(day("day-1", 1));
        ItineraryNode node = node("node-1", "day-1", 1000);
        node.setNote("旧备注");
        when(itineraryNodeMapper.selectOne(any(QueryWrapper.class))).thenReturn(node);
        when(itineraryNodeMapper.updateById(node)).thenReturn(1);

        LoginUserContext.setUserId("u-1");
        try {
            var resp = itineraryService.updateNode("trip-1", "day-1", "node-1", new ItineraryNodeUpdateReq(
                    null, null, null, null, null, "", null, null));
            assertEquals(null, resp.note());
        } finally {
            LoginUserContext.clear();
        }

        assertEquals(null, node.getNote());
        verify(itineraryNodeMapper).updateById(node);
    }

    @Test
    void createNode_completedTrip_rejects() {
        LocalDate today = DateTimeUtil.todayBusiness();
        Trip trip = tripWithDates("u-1", today.minusDays(10), today.minusDays(5));
        when(tripMapper.selectById("trip-1")).thenReturn(trip);

        LoginUserContext.setUserId("u-1");
        try {
            BizException ex = assertThrows(BizException.class, () -> itineraryService.createNode(
                    "trip-1", "day-1",
                    new ItineraryNodeCreateReq("浅草寺", "attraction", null, null, null, null, null, null, null)));
            assertEquals(ErrorCode.TRIP_COMPLETED_CONTENT_LOCKED, ex.getErrorCode());
            verify(itineraryNodeMapper, never()).insert(any(ItineraryNode.class));
        } finally {
            LoginUserContext.clear();
        }
    }

    @Test
    void deleteNode_completedTrip_rejects() {
        LocalDate today = DateTimeUtil.todayBusiness();
        Trip trip = tripWithDates("u-1", today.minusDays(10), today.minusDays(5));
        when(tripMapper.selectById("trip-1")).thenReturn(trip);

        LoginUserContext.setUserId("u-1");
        try {
            BizException ex = assertThrows(BizException.class, () ->
                    itineraryService.deleteNode("trip-1", "day-1", "node-1"));
            assertEquals(ErrorCode.TRIP_COMPLETED_CONTENT_LOCKED, ex.getErrorCode());
            verify(itineraryNodeMapper, never()).delete(any(UpdateWrapper.class));
        } finally {
            LoginUserContext.clear();
        }
    }

    @Test
    void reorderNodes_requiresExactNodeSetAndUpdatesByOrder() {
        when(tripMapper.selectById("trip-1")).thenReturn(trip("u-1"));
        when(tripDayMapper.selectOne(any(QueryWrapper.class))).thenReturn(day("day-1", 1));
        when(itineraryNodeMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(
                node("node-1", "day-1", 1000),
                node("node-2", "day-1", 2000)
        ));

        LoginUserContext.setUserId("u-1");
        try {
            itineraryService.reorderNodes("trip-1", "day-1",
                    new ItineraryNodeReorderReq(List.of("node-2", "node-1")));
        } finally {
            LoginUserContext.clear();
        }

        verify(itineraryNodeMapper, times(2)).update(isNull(), any(UpdateWrapper.class));
    }

    @Test
    void reorderNodes_rejectsMissingNode() {
        when(tripMapper.selectById("trip-1")).thenReturn(trip("u-1"));
        when(tripDayMapper.selectOne(any(QueryWrapper.class))).thenReturn(day("day-1", 1));
        when(itineraryNodeMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(
                node("node-1", "day-1", 1000),
                node("node-2", "day-1", 2000)
        ));

        LoginUserContext.setUserId("u-1");
        try {
            assertThrows(BizException.class, () -> itineraryService.reorderNodes("trip-1", "day-1",
                    new ItineraryNodeReorderReq(List.of("node-1"))));
        } finally {
            LoginUserContext.clear();
        }
    }

    private Trip trip(String userId) {
        Trip trip = new Trip();
        trip.setId("trip-1");
        trip.setUserId(userId);
        return trip;
    }

    private Trip tripWithDates(String userId, LocalDate startDate, LocalDate endDate) {
        Trip trip = trip(userId);
        trip.setTitle("东京 5 日");
        trip.setStartDate(startDate);
        trip.setEndDate(endDate);
        trip.setBaseCurrency("CNY");
        return trip;
    }

    private TripDay day(String id, int dayIndex) {
        TripDay day = new TripDay();
        day.setId(id);
        day.setTripId("trip-1");
        day.setUserId("u-1");
        day.setDayIndex(dayIndex);
        day.setDate(LocalDate.of(2026, 7, dayIndex));
        return day;
    }


    private ItineraryNodeLocationReq location(String name) {
        return new ItineraryNodeLocationReq(
                null, name, null, null, null, null, null, null, null, null, "MANUAL", null, null);
    }

    private ItineraryNode node(String id, String dayId, int sortOrder) {
        ItineraryNode node = new ItineraryNode();
        node.setId(id);
        node.setTripId("trip-1");
        node.setTripDayId(dayId);
        node.setUserId("u-1");
        node.setTitle("浅草寺");
        node.setType("attraction");
        node.setStartTime(LocalTime.of(9, 0));
        node.setDetailsJson("{}");
        node.setSortOrder(sortOrder);
        node.setCreatedAt(LocalDateTime.of(2026, 5, 13, 10, 0));
        return node;
    }

    @Test
    void createNode_savesTransportDetails() {
        when(tripMapper.selectById("trip-1")).thenReturn(trip("u-1"));
        when(tripDayMapper.selectOne(any(QueryWrapper.class))).thenReturn(day("day-1", 1));
        when(itineraryNodeMapper.selectMaxSortOrder("trip-1", "day-1", "u-1")).thenReturn(null);
        doAnswer(invocation -> {
            ItineraryNode node = invocation.getArgument(0);
            node.setId("node-1");
            node.setCreatedAt(LocalDateTime.of(2026, 5, 13, 10, 0));
            return 1;
        }).when(itineraryNodeMapper).insert(any(ItineraryNode.class));

        LoginUserContext.setUserId("u-1");
        try {
            var resp = itineraryService.createNode("trip-1", "day-1", new ItineraryNodeCreateReq(
                    "虹桥 → 成田",
                    "transport",
                    "09:00",
                    "13:20",
                    null,
                    null,
                    location("上海虹桥国际机场"),
                    Map.of(
                            "kind", "transport",
                            "mode", "flight",
                            "flightNo", "MU523",
                            "airline", "东航"
                    ),
                    null
            ));

            assertEquals("transport", resp.type());
            assertEquals("flight", resp.details().get("mode"));
            assertEquals("MU523", resp.details().get("flightNo"));
        } finally {
            LoginUserContext.clear();
        }

        ArgumentCaptor<ItineraryNode> captor = ArgumentCaptor.forClass(ItineraryNode.class);
        verify(itineraryNodeMapper).insert(captor.capture());
        assertTrue(captor.getValue().getDetailsJson().contains("MU523"));
    }

    @Test
    void createNode_rejectsTransportDetailsWithoutMode() {
        when(tripMapper.selectById("trip-1")).thenReturn(trip("u-1"));
        when(tripDayMapper.selectOne(any(QueryWrapper.class))).thenReturn(day("day-1", 1));

        LoginUserContext.setUserId("u-1");
        try {
            assertThrows(BizException.class, () -> itineraryService.createNode(
                    "trip-1", "day-1",
                    new ItineraryNodeCreateReq(
                            "行程",
                            "transport",
                            null,
                            null,
                            null,
                            null,
                            location("机场"),
                            Map.of("kind", "transport", "flightNo", "MU523"),
                            null)));
        } finally {
            LoginUserContext.clear();
        }
    }

    @Test
    void createNode_savesHotelDetails() {
        when(tripMapper.selectById("trip-1")).thenReturn(trip("u-1"));
        when(tripDayMapper.selectOne(any(QueryWrapper.class))).thenReturn(day("day-1", 1));
        when(itineraryNodeMapper.selectMaxSortOrder("trip-1", "day-1", "u-1")).thenReturn(null);
        doAnswer(invocation -> {
            ItineraryNode node = invocation.getArgument(0);
            node.setId("node-1");
            node.setCreatedAt(LocalDateTime.of(2026, 5, 13, 10, 0));
            return 1;
        }).when(itineraryNodeMapper).insert(any(ItineraryNode.class));

        LoginUserContext.setUserId("u-1");
        try {
            var resp = itineraryService.createNode("trip-1", "day-1", new ItineraryNodeCreateReq(
                    "京王广场酒店",
                    "hotel",
                    "15:00",
                    null,
                    null,
                    null,
                    location("京王广场酒店"),
                    Map.of(
                            "kind", "hotel",
                            "confirmationCode", "ABC123",
                            "checkInTime", "15:00",
                            "checkOutTime", "11:00"
                    ),
                    null
            ));

            assertEquals("hotel", resp.type());
            assertEquals("ABC123", resp.details().get("confirmationCode"));
            assertEquals("11:00", resp.details().get("checkOutTime"));
        } finally {
            LoginUserContext.clear();
        }
    }

    @Test
    void createNode_hotelWithCheckOutDate_materializesStayGroup() {
        // 使用「今天起」的旅程，避免 isTripCompleted 拦截
        LocalDate start = DateTimeUtil.todayBusiness().plusDays(10);
        LocalDate checkIn = start;
        LocalDate checkOut = start.plusDays(3); // 3 晚
        LocalDate end = start.plusDays(5);
        Trip trip = tripWithDates("u-1", start, end);
        when(tripMapper.selectById("trip-1")).thenReturn(trip);
        TripDay day1 = day("day-1", 1);
        day1.setDate(checkIn);
        TripDay day2 = day("day-2", 2);
        day2.setDate(checkIn.plusDays(1));
        TripDay day3 = day("day-3", 3);
        day3.setDate(checkIn.plusDays(2));
        TripDay day4 = day("day-4", 4);
        day4.setDate(checkIn.plusDays(3));
        when(tripDayMapper.selectOne(any(QueryWrapper.class))).thenReturn(day1);
        when(tripDayMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(day1, day2, day3, day4));
        when(itineraryNodeMapper.selectMaxSortOrderByTripDay(any(), any())).thenReturn(null);
        when(itineraryNodeMapper.selectMaxSortOrder(any(), any(), any())).thenReturn(null);
        doAnswer(invocation -> {
            ItineraryNode node = invocation.getArgument(0);
            if (node.getId() == null) {
                node.setId("node-" + System.nanoTime());
            }
            node.setCreatedAt(LocalDateTime.of(2026, 5, 13, 10, 0));
            return 1;
        }).when(itineraryNodeMapper).insert(any(ItineraryNode.class));

        LoginUserContext.setUserId("u-1");
        try {
            var resp = itineraryService.createNode("trip-1", "day-1", new ItineraryNodeCreateReq(
                    "京王广场酒店",
                    "hotel",
                    "15:00",
                    null,
                    null,
                    null,
                    location("京王广场酒店"),
                    Map.of(
                            "kind", "hotel",
                            "checkOutDate", checkOut.toString(),
                            "confirmationCode", "ABC"
                    ),
                    null
            ));

            assertEquals("hotel", resp.type());
            assertEquals("check_in", resp.details().get("stayRole"));
            assertEquals(checkOut.toString(), resp.details().get("checkOutDate"));
            assertEquals(3, resp.details().get("totalNights"));
            assertTrue(resp.details().get("stayGroupId") != null);
        } finally {
            LoginUserContext.clear();
        }

        verify(itineraryNodeMapper, times(3)).insert(any(ItineraryNode.class));
    }

    @Test
    void deleteNode_stayProjection_rejects() {
        LocalDate today = DateTimeUtil.todayBusiness();
        when(tripMapper.selectById("trip-1")).thenReturn(
                tripWithDates("u-1", today.minusDays(1), today.plusDays(5)));
        when(tripDayMapper.selectOne(any(QueryWrapper.class))).thenReturn(day("day-2", 2));
        ItineraryNode projection = node("node-stay", "day-2", 1000);
        projection.setType("hotel");
        projection.setDetailsJson(
                "{\"kind\":\"hotel\",\"stayGroupId\":\"g-1\",\"stayRole\":\"stay\","
                        + "\"checkInDate\":\"" + today + "\",\"checkOutDate\":\"" + today.plusDays(3) + "\","
                        + "\"nightIndex\":2,\"totalNights\":3}");
        when(itineraryNodeMapper.selectOne(any(QueryWrapper.class))).thenReturn(projection);

        LoginUserContext.setUserId("u-1");
        try {
            BizException ex = assertThrows(BizException.class, () ->
                    itineraryService.deleteNode("trip-1", "day-2", "node-stay"));
            assertEquals(ErrorCode.ITINERARY_STAY_PROJECTION_DELETE_FORBIDDEN, ex.getErrorCode());
            verify(itineraryNodeMapper, never()).delete(any(UpdateWrapper.class));
            verify(itineraryNodeMapper, never()).deleteById(any(String.class));
        } finally {
            LoginUserContext.clear();
        }
    }

    @Test
    void createNode_hotelCheckOutNotAfterCheckIn_rejects() {
        LocalDate start = DateTimeUtil.todayBusiness().plusDays(10);
        Trip trip = tripWithDates("u-1", start, start.plusDays(5));
        when(tripMapper.selectById("trip-1")).thenReturn(trip);
        TripDay day1 = day("day-1", 1);
        day1.setDate(start);
        when(tripDayMapper.selectOne(any(QueryWrapper.class))).thenReturn(day1);

        LoginUserContext.setUserId("u-1");
        try {
            BizException ex = assertThrows(BizException.class, () -> itineraryService.createNode(
                    "trip-1", "day-1",
                    new ItineraryNodeCreateReq(
                            "酒店",
                            "hotel",
                            null,
                            null,
                            null,
                            null,
                            location("酒店"),
                            Map.of("kind", "hotel", "checkOutDate", start.toString()),
                            null)));
            assertEquals(ErrorCode.ITINERARY_STAY_DATE_INVALID, ex.getErrorCode());
        } finally {
            LoginUserContext.clear();
        }
    }

    @Test
    void reconcileHotelStays_clipsCheckOutBeyondTripEnd() {
        LocalDate start = DateTimeUtil.todayBusiness().plusDays(20);
        LocalDate end = start.plusDays(2); // 3 天旅程
        Trip trip = tripWithDates("u-1", start, end);
        when(tripMapper.selectById("trip-1")).thenReturn(trip);

        TripDay day1 = day("day-1", 1);
        day1.setDate(start);
        TripDay day2 = day("day-2", 2);
        day2.setDate(start.plusDays(1));
        TripDay day3 = day("day-3", 3);
        day3.setDate(start.plusDays(2));
        when(tripDayMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(day1, day2, day3));

        // 原 stay 计划 5 晚，离店远超旅程结束
        String oldOut = start.plusDays(5).toString();
        String groupJson = "{\"kind\":\"hotel\",\"stayGroupId\":\"g-clip\",\"stayRole\":\"check_in\","
                + "\"checkInDate\":\"" + start + "\",\"checkOutDate\":\"" + oldOut + "\","
                + "\"nightIndex\":1,\"totalNights\":5,\"confirmationCode\":\"X\"}";
        ItineraryNode checkIn = node("node-ci", "day-1", 1000);
        checkIn.setType("hotel");
        checkIn.setDetailsJson(groupJson);
        ItineraryNode stay2 = node("node-s2", "day-2", 1000);
        stay2.setType("hotel");
        stay2.setDetailsJson(groupJson.replace("check_in", "stay").replace("\"nightIndex\":1", "\"nightIndex\":2"));

        when(itineraryNodeMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(checkIn, stay2));
        when(itineraryNodeMapper.selectById("node-ci")).thenReturn(checkIn);
        when(itineraryNodeMapper.selectMaxSortOrderByTripDay(any(), any())).thenReturn(null);
        when(itineraryNodeMapper.selectMaxSortOrder(any(), any(), any())).thenReturn(null);
        when(itineraryNodeMapper.updateById(any(ItineraryNode.class))).thenReturn(1);
        doAnswer(invocation -> {
            ItineraryNode n = invocation.getArgument(0);
            if (n.getId() == null) {
                n.setId("node-new-" + System.nanoTime());
            }
            return 1;
        }).when(itineraryNodeMapper).insert(any(ItineraryNode.class));

        itineraryService.reconcileHotelStaysAfterTripDateChange("trip-1");

        // 至少更新过主节点 details（裁剪后的 checkOut = end+1）
        ArgumentCaptor<ItineraryNode> updateCaptor = ArgumentCaptor.forClass(ItineraryNode.class);
        verify(itineraryNodeMapper, org.mockito.Mockito.atLeastOnce()).updateById(updateCaptor.capture());
        boolean clipped = updateCaptor.getAllValues().stream()
                .map(ItineraryNode::getDetailsJson)
                .filter(j -> j != null)
                .anyMatch(j -> j.contains(end.plusDays(1).toString()));
        assertTrue(clipped);
    }

    @Test
    void updateNode_completedTripDetailsChange_rejects() {
        LocalDate today = DateTimeUtil.todayBusiness();
        Trip trip = tripWithDates("u-1", today.minusDays(10), today.minusDays(5));
        when(tripMapper.selectById("trip-1")).thenReturn(trip);
        when(tripDayMapper.selectOne(any(QueryWrapper.class))).thenReturn(day("day-1", 1));
        ItineraryNode node = node("node-1", "day-1", 1000);
        when(itineraryNodeMapper.selectOne(any(QueryWrapper.class))).thenReturn(node);

        LoginUserContext.setUserId("u-1");
        try {
            BizException ex = assertThrows(BizException.class, () -> itineraryService.updateNode(
                    "trip-1", "day-1", "node-1",
                    new ItineraryNodeUpdateReq(null, null, null, null, null, null, null,
                            Map.of("kind", "hotel", "confirmationCode", "X"))));
            assertEquals(ErrorCode.ITINERARY_COMPLETED_NODE_NOTE_ONLY, ex.getErrorCode());
            verify(itineraryNodeMapper, never()).updateById(any(ItineraryNode.class));
        } finally {
            LoginUserContext.clear();
        }
    }

    @Test
    void createNode_hotelWithSavedPlaceId_appliesPlaceAndRecordsUsage() {
        when(tripMapper.selectById("trip-1")).thenReturn(trip("u-1"));
        when(tripDayMapper.selectOne(any(QueryWrapper.class))).thenReturn(day("day-1", 1));
        when(itineraryNodeMapper.selectMaxSortOrder("trip-1", "day-1", "u-1")).thenReturn(null);

        com.tracci.module.place.entity.Place place = new com.tracci.module.place.entity.Place();
        place.setId("place-hotel");
        place.setCanonicalName("京王广场酒店");
        place.setAddress("新宿");
        place.setCityName("东京");
        place.setCountry("日本");
        place.setDestinationKey("adcode:131016");
        place.setLatitude(new BigDecimal("35.69"));
        place.setLongitude(new BigDecimal("139.70"));
        when(savedPlaceService.resolveForItineraryNode("u-1", "sp-1", "trip-1")).thenReturn(place);

        doAnswer(invocation -> {
            ItineraryNode node = invocation.getArgument(0);
            node.setId("node-hotel");
            node.setCreatedAt(LocalDateTime.of(2026, 5, 13, 10, 0));
            return 1;
        }).when(itineraryNodeMapper).insert(any(ItineraryNode.class));

        LoginUserContext.setUserId("u-1");
        try {
            // 仅传 savedPlaceId，不传 location
            var resp = itineraryService.createNode("trip-1", "day-1", new ItineraryNodeCreateReq(
                    null,
                    "hotel",
                    "15:00",
                    null,
                    null,
                    null,
                    null,
                    Map.of("kind", "hotel", "confirmationCode", "H1"),
                    "sp-1"
            ));

            assertEquals("京王广场酒店", resp.location().name());
            assertEquals("新宿", resp.location().address());
            assertEquals("新宿", resp.address());
            assertEquals("place-hotel", resp.location().placeId());
            assertEquals("131016", resp.location().adcode());
            verify(savedPlaceService).recordNodeUsage("u-1", "sp-1", "trip-1", "node-hotel");
        } finally {
            LoginUserContext.clear();
        }
    }

    @Test
    void createNode_attractionWithSavedPlaceId_appliesPlace() {
        when(tripMapper.selectById("trip-1")).thenReturn(trip("u-1"));
        when(tripDayMapper.selectOne(any(QueryWrapper.class))).thenReturn(day("day-1", 1));
        when(itineraryNodeMapper.selectMaxSortOrder("trip-1", "day-1", "u-1")).thenReturn(1000);

        com.tracci.module.place.entity.Place place = new com.tracci.module.place.entity.Place();
        place.setId("place-poi");
        place.setCanonicalName("浅草寺");
        place.setCityName("东京");
        when(savedPlaceService.resolveForItineraryNode("u-1", "sp-2", "trip-1")).thenReturn(place);

        doAnswer(invocation -> {
            ItineraryNode node = invocation.getArgument(0);
            node.setId("node-poi");
            node.setCreatedAt(LocalDateTime.of(2026, 5, 13, 10, 0));
            return 1;
        }).when(itineraryNodeMapper).insert(any(ItineraryNode.class));

        LoginUserContext.setUserId("u-1");
        try {
            var resp = itineraryService.createNode("trip-1", "day-1", new ItineraryNodeCreateReq(
                    null, "attraction", null, null, null, null, null, null, "sp-2"));
            assertEquals("浅草寺", resp.location().name());
            assertEquals("place-poi", resp.location().placeId());
            verify(savedPlaceService).recordNodeUsage("u-1", "sp-2", "trip-1", "node-poi");
        } finally {
            LoginUserContext.clear();
        }
    }
}
