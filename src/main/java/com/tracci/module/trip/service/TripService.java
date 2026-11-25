package com.tracci.module.trip.service;

import com.tracci.common.response.PageResponse;
import com.tracci.module.trip.dto.request.TripCreateReq;
import com.tracci.module.trip.dto.request.TripUpdateReq;
import com.tracci.module.trip.dto.response.TripCreateResp;
import com.tracci.module.trip.dto.response.TripDetailResp;
import com.tracci.module.trip.dto.response.TripListResp;

public interface TripService {

    TripCreateResp createTrip(TripCreateReq req);

    PageResponse<TripListResp> listTrips(int page, int pageSize, String status, String keyword);

    TripDetailResp getTrip(String tripId);

    TripDetailResp updateTrip(String tripId, TripUpdateReq req);

    void deleteTrip(String tripId);
}
