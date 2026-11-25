package com.tracci.module.demo.dto.response;

public record DemoJourneyCapabilitiesResp(
        boolean readOnly,
        boolean canEditTrip,
        boolean canAddNode,
        boolean canEditJournal,
        boolean canManageExpense,
        boolean canManageMembers,
        boolean canInvite,
        boolean canDelete,
        boolean canShare,
        boolean canExport,
        boolean canApplyAiChanges
) {

    public static DemoJourneyCapabilitiesResp allReadOnly() {
        return new DemoJourneyCapabilitiesResp(
                true,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false);
    }
}
