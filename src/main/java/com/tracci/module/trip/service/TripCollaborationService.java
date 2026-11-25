package com.tracci.module.trip.service;

import com.tracci.module.trip.dto.request.TripInvitationCreateReq;
import com.tracci.module.trip.dto.request.TripInvitationJoinReq;
import com.tracci.module.trip.dto.request.TripMemberUpdateReq;
import com.tracci.module.trip.dto.request.TripOwnershipTransferReq;
import com.tracci.module.trip.dto.response.TripInvitationCreateResp;
import com.tracci.module.trip.dto.response.TripInvitationJoinResp;
import com.tracci.module.trip.dto.response.TripMemberResp;
import com.tracci.module.trip.dto.response.TripOwnershipTransferResp;

import java.util.List;

public interface TripCollaborationService {

    TripInvitationCreateResp createInvitation(String tripId, TripInvitationCreateReq req);

    TripInvitationJoinResp joinInvitation(TripInvitationJoinReq req);

    List<TripMemberResp> listMembers(String tripId);

    TripMemberResp updateMember(String tripId, String memberId, TripMemberUpdateReq req);

    TripOwnershipTransferResp transferOwnership(String tripId, TripOwnershipTransferReq req);

    void removeMember(String tripId, String memberId);
}
