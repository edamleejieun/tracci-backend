package com.tracci.module.trip.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tracci.common.util.DateTimeUtil;
import com.tracci.common.context.LoginUserContext;
import com.tracci.common.response.PageResponse;
import com.tracci.module.trip.dto.response.TripActivityLogResp;
import com.tracci.module.trip.entity.TripActivityLog;
import com.tracci.module.trip.repository.TripActivityLogMapper;
import com.tracci.module.trip.enums.TripPermission;
import com.tracci.module.user.entity.User;
import com.tracci.module.user.repository.UserMapper;
import com.tracci.module.user.support.UserDisplaySupport;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TripActivityLogServiceImpl implements TripActivityLogService {

    private static final TypeReference<List<TripActivityLogResp.FieldChange>> FIELD_CHANGE_LIST =
            new TypeReference<>() {
            };

    private final TripActivityLogMapper activityLogMapper;
    private final UserMapper userMapper;
    private final TripAccessService tripAccessService;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public void record(String tripId, String actorUserId, String actorMemberId, String action,
                       String targetType, String targetId, List<TripActivityLogResp.FieldChange> changes, String summary) {
        TripActivityLog log = new TripActivityLog();
        log.setTripId(tripId);
        log.setActorUserId(actorUserId);
        log.setActorMemberId(actorMemberId);
        log.setAction(action);
        log.setTargetType(targetType);
        log.setTargetId(targetId);
        log.setFieldChangesJson(writeChanges(changes == null ? List.of() : changes));
        log.setSummary(summary);
        activityLogMapper.insert(log);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<TripActivityLogResp> listLogs(String tripId, int page, int pageSize) {
        String userId = LoginUserContext.requireUserId();
        tripAccessService.requirePermission(tripId, userId, TripPermission.READ);
        int safePage = Math.max(page, 1);
        int safePageSize = Math.min(Math.max(pageSize, 1), 100);
        Page<TripActivityLog> result = activityLogMapper.pageJourneyHistoryByTripId(
                new Page<>(safePage, safePageSize), tripId, userId);
        List<String> actorUserIds = result.getRecords().stream()
                .map(TripActivityLog::getActorUserId)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        Map<String, User> usersById = actorUserIds.isEmpty()
                ? Map.of()
                : userMapper.selectBatchIds(actorUserIds).stream()
                        .collect(Collectors.toMap(User::getId, user -> user, (left, right) -> left));
        List<TripActivityLogResp> list = result.getRecords().stream()
                .map(log -> toResp(log, usersById.get(log.getActorUserId())))
                .toList();
        return PageResponse.of(list, result.getTotal(), safePage, safePageSize);
    }

    private TripActivityLogResp toResp(TripActivityLog log, User actor) {
        return new TripActivityLogResp(
                log.getId(),
                log.getTripId(),
                new TripActivityLogResp.Actor(log.getActorUserId(), log.getActorMemberId(),
                        UserDisplaySupport.nickname(actor)),
                log.getAction(),
                log.getTargetType(),
                log.getTargetId(),
                log.getSummary(),
                readChanges(log.getFieldChangesJson()),
                DateTimeUtil.toInstantString(log.getCreatedAt()));
    }

    private String writeChanges(List<TripActivityLogResp.FieldChange> changes) {
        try {
            return objectMapper.writeValueAsString(changes);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }

    private List<TripActivityLogResp.FieldChange> readChanges(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, FIELD_CHANGE_LIST);
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }
}
