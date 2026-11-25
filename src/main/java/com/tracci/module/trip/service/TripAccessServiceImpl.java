package com.tracci.module.trip.service;

import com.tracci.common.exception.ForbiddenException;
import com.tracci.common.exception.NotFoundException;
import com.tracci.common.response.ErrorCode;
import com.tracci.module.trip.entity.Trip;
import com.tracci.module.trip.entity.TripMember;
import com.tracci.module.trip.enums.TripMemberRole;
import com.tracci.module.trip.enums.TripPermission;
import com.tracci.module.trip.repository.TripMapper;
import com.tracci.module.trip.repository.TripMemberMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TripAccessServiceImpl implements TripAccessService {

    private final TripMapper tripMapper;
    private final TripMemberMapper tripMemberMapper;

    @Override
    public TripAccess requirePermission(String tripId, String userId, TripPermission permission) {
        TripAccess access = requireMember(tripId, userId);
        if (!access.allows(permission)) {
            throw new ForbiddenException();
        }
        return access;
    }

    private TripAccess requireMember(String tripId, String userId) {
        Trip trip = tripMapper.selectById(tripId);
        if (trip == null) {
            throw new NotFoundException(ErrorCode.TRIP_NOT_FOUND);
        }
        TripMember member = tripMemberMapper.findActiveByTripIdAndUserId(tripId, userId);
        if (member == null) {
            throw new ForbiddenException();
        }
        TripMemberRole role;
        try {
            role = TripMemberRole.fromValue(member.getRole());
        } catch (IllegalArgumentException ex) {
            throw new ForbiddenException();
        }
        return new TripAccess(trip, member, role, role.permissions());
    }
}
