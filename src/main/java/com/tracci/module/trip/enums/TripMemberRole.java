package com.tracci.module.trip.enums;

import java.util.EnumSet;
import java.util.Set;

public enum TripMemberRole {

    OWNER("owner", EnumSet.allOf(TripPermission.class)),
    EDITOR("editor", EnumSet.of(
            TripPermission.READ,
            TripPermission.EDIT_CONTENT,
            TripPermission.CREATE_INVITATION,
            TripPermission.MANAGE_SHARE)),
    VIEWER("viewer", EnumSet.of(TripPermission.READ));

    private final String value;
    private final Set<TripPermission> permissions;

    TripMemberRole(String value, Set<TripPermission> permissions) {
        this.value = value;
        this.permissions = Set.copyOf(permissions);
    }

    public String value() {
        return value;
    }

    public boolean isOwner() {
        return this == OWNER;
    }

    public boolean hasPermission(TripPermission permission) {
        return permissions.contains(permission);
    }

    public Set<TripPermission> permissions() {
        return permissions;
    }

    public static TripMemberRole fromValue(String value) {
        for (TripMemberRole role : values()) {
            if (role.value.equalsIgnoreCase(value)) {
                return role;
            }
        }
        throw new IllegalArgumentException("Unsupported trip member role: " + value);
    }
}
