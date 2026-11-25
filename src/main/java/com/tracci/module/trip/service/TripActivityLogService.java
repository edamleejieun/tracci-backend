package com.tracci.module.trip.service;

import com.tracci.common.response.PageResponse;
import com.tracci.module.trip.dto.response.TripActivityLogResp;

import java.util.List;

public interface TripActivityLogService {

    void record(String tripId, String actorUserId, String actorMemberId, String action,
                String targetType, String targetId, List<TripActivityLogResp.FieldChange> changes, String summary);

    PageResponse<TripActivityLogResp> listLogs(String tripId, int page, int pageSize);
}
