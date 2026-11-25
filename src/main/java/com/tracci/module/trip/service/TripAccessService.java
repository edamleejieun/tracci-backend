package com.tracci.module.trip.service;

import com.tracci.module.trip.entity.Trip;
import com.tracci.module.trip.entity.TripMember;
import com.tracci.module.trip.enums.TripMemberRole;
import com.tracci.module.trip.enums.TripPermission;

import java.util.Set;

public interface TripAccessService {

    TripAccess requirePermission(String tripId, String userId, TripPermission permission);

    record TripAccess(
            Trip trip,
            TripMember member,
            TripMemberRole role,
            Set<TripPermission> permissions
    ) {
        public TripAccess(Trip trip, TripMember member) {
            this(
                    trip,
                    member,
                    roleOf(member),
                    permissionsOf(member));
        }

        public boolean allows(TripPermission permission) {
            return permissions.contains(permission);
        }

        private static TripMemberRole roleOf(TripMember member) {
            if (member == null || member.getRole() == null || member.getRole().isBlank()) {
                return null;
            }
            return TripMemberRole.fromValue(member.getRole());
        }

        private static Set<TripPermission> permissionsOf(TripMember member) {
            TripMemberRole role = roleOf(member);
            return role == null ? Set.of() : role.permissions();
        }
    }
}
