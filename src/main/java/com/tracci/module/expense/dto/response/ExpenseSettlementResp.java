package com.tracci.module.expense.dto.response;

import java.util.List;

public record ExpenseSettlementResp(
        String tripId,
        String currency,
        String totalGroupAmount,
        List<MemberSettlement> members,
        List<SuggestedTransfer> suggestedTransfers
) {

    public record MemberSettlement(
            String memberId,
            String userId,
            String displayName,
            /** Sum paid by this member as payer for group expenses. */
            String paidAmount,
            /** Sum allocated to this member by group-expense split records. */
            String shareAmount,
            /** paidAmount - shareAmount; positive is receivable, negative is payable. */
            String balanceAmount
    ) {
    }

    public record SuggestedTransfer(
            String fromMemberId,
            String toMemberId,
            String amount
    ) {
    }
}
