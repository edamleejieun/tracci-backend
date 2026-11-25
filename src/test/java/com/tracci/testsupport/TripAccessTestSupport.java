package com.tracci.testsupport;

import com.tracci.common.exception.ForbiddenException;
import com.tracci.common.exception.NotFoundException;
import com.tracci.common.response.ErrorCode;
import com.tracci.module.trip.entity.Trip;
import com.tracci.module.trip.entity.TripMember;
import com.tracci.module.trip.enums.TripMemberRole;
import com.tracci.module.trip.repository.TripMapper;
import com.tracci.module.trip.service.TripAccessService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;

public final class TripAccessTestSupport {

    private TripAccessTestSupport() {
    }

    /**
     * 为仍以单人 owner 数据构造的 Service 单测提供统一授权夹具。
     * 生产代码不允许回退 trips.user_id；该兼容仅存在于测试层。
     */
    public static void stubOwnerAccess(TripAccessService accessService, TripMapper tripMapper) {
        lenient().when(accessService.requirePermission(anyString(), anyString(), any()))
                .thenAnswer(invocation -> {
                    String tripId = invocation.getArgument(0);
                    String userId = invocation.getArgument(1);
                    Trip trip = tripMapper.selectById(tripId);
                    if (trip == null) {
                        trip = tripMapper.listMemberTrips(userId).stream()
                                .filter(candidate -> tripId.equals(candidate.getId()))
                                .findFirst()
                                .orElse(null);
                    }
                    if (trip == null) {
                        throw new NotFoundException(ErrorCode.TRIP_NOT_FOUND);
                    }
                    if (!userId.equals(trip.getUserId())) {
                        throw new ForbiddenException();
                    }
                    TripMember owner = new TripMember();
                    owner.setId("owner-" + tripId);
                    owner.setTripId(tripId);
                    owner.setUserId(userId);
                    owner.setRole("owner");
                    owner.setStatus("active");
                    return new TripAccessService.TripAccess(
                            trip, owner, TripMemberRole.OWNER, TripMemberRole.OWNER.permissions());
                });
    }
}
