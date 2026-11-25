package com.tracci.module.trip.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tracci.common.context.LoginUserContext;
import com.tracci.module.trip.entity.TripActivityLog;
import com.tracci.module.trip.enums.TripPermission;
import com.tracci.module.trip.repository.TripActivityLogMapper;
import com.tracci.module.user.entity.User;
import com.tracci.module.user.repository.UserMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TripActivityLogServiceImplTest {

    @Mock
    private TripActivityLogMapper activityLogMapper;

    @Mock
    private UserMapper userMapper;

    @Mock
    private TripAccessService tripAccessService;

    @Test
    void listLogs_readsJourneyHistoryOnly() {
        TripActivityLogServiceImpl service = new TripActivityLogServiceImpl(
                activityLogMapper,
                userMapper,
                tripAccessService,
                new ObjectMapper());
        TripActivityLog log = log("log-1", "JOURNAL", "JOURNAL_CREATED");
        log.setFieldChangesJson("""
                [{"field":"photoCount","oldValue":null,"newValue":"2"}]
                """);
        Page<TripActivityLog> page = new Page<>(1, 50);
        page.setRecords(List.of(log));
        page.setTotal(1);
        when(activityLogMapper.pageJourneyHistoryByTripId(any(Page.class), eq("trip-1"), eq("u-1"))).thenReturn(page);
        User actor = new User();
        actor.setId("u-1");
        actor.setNickname("Alice");
        when(userMapper.selectBatchIds(anyCollection())).thenReturn(List.of(actor));

        LoginUserContext.setUserId("u-1");
        try {
            var response = service.listLogs("trip-1", 1, 50);

            assertEquals(1, response.list().size());
            assertEquals("JOURNAL", response.list().getFirst().targetType());
            assertEquals("Alice", response.list().getFirst().actor().nickname());
            assertEquals(1, response.list().getFirst().fieldChanges().size());
        verify(tripAccessService).requirePermission("trip-1", "u-1", TripPermission.READ);
        } finally {
            LoginUserContext.clear();
        }

        ArgumentCaptor<Page<TripActivityLog>> pageCaptor = ArgumentCaptor.forClass(Page.class);
        verify(activityLogMapper).pageJourneyHistoryByTripId(pageCaptor.capture(), eq("trip-1"), eq("u-1"));
        assertEquals(1, pageCaptor.getValue().getCurrent());
        assertEquals(50, pageCaptor.getValue().getSize());
    }

    @Test
    void listLogs_usesDeletedUserFallbackWhenActorCannotBeLoaded() {
        TripActivityLogServiceImpl service = new TripActivityLogServiceImpl(
                activityLogMapper,
                userMapper,
                tripAccessService,
                new ObjectMapper());
        Page<TripActivityLog> page = new Page<>(1, 50);
        page.setRecords(List.of(log("log-1", "TRIP", "TRIP_UPDATED")));
        page.setTotal(1);
        when(activityLogMapper.pageJourneyHistoryByTripId(any(Page.class), eq("trip-1"), eq("u-1")))
                .thenReturn(page);
        when(userMapper.selectBatchIds(anyCollection())).thenReturn(List.of());

        LoginUserContext.setUserId("u-1");
        try {
            var response = service.listLogs("trip-1", 1, 50);

            assertEquals("已注销用户", response.list().getFirst().actor().nickname());
        } finally {
            LoginUserContext.clear();
        }
    }

    private TripActivityLog log(String id, String targetType, String action) {
        TripActivityLog log = new TripActivityLog();
        log.setId(id);
        log.setTripId("trip-1");
        log.setActorUserId("u-1");
        log.setActorMemberId("member-1");
        log.setAction(action);
        log.setTargetType(targetType);
        log.setTargetId("target-1");
        log.setSummary("更新了旅程内容");
        log.setCreatedAt(LocalDateTime.of(2026, 6, 19, 10, 0));
        return log;
    }
}
