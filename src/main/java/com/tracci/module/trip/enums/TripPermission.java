package com.tracci.module.trip.enums;

public enum TripPermission {

    READ("trip.read"),
    EDIT_CONTENT("trip.content.edit"),
    UPDATE_TRIP("trip.update"),
    DELETE_TRIP("trip.delete"),
    CREATE_INVITATION("trip.invitation.create"),
    MANAGE_MEMBERS("trip.members.manage"),
    TRANSFER_OWNERSHIP("trip.ownership.transfer"),
    MANAGE_SHARE("trip.share.manage");

    private final String value;

    TripPermission(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
