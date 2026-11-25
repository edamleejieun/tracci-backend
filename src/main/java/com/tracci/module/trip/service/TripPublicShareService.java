package com.tracci.module.trip.service;

import com.tracci.module.trip.dto.request.TripShareUpdateReq;
import com.tracci.module.trip.dto.response.PublicCoEditorInviteResp;
import com.tracci.module.trip.dto.response.PublicJourneyShareResp;
import com.tracci.module.trip.dto.response.TripShareResp;

public interface TripPublicShareService {

    TripShareResp updateShare(String tripId, TripShareUpdateReq req);

    PublicJourneyShareResp getJourneyShare(String tripId);

    PublicCoEditorInviteResp validateCoEditorInvite(String tripId, String inviteCode);
}
