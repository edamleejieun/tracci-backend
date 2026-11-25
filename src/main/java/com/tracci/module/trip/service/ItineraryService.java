package com.tracci.module.trip.service;

import com.tracci.module.trip.dto.request.ItineraryNodeCreateReq;
import com.tracci.module.trip.dto.request.ItineraryNodeReorderReq;
import com.tracci.module.trip.dto.request.ItineraryNodeUpdateReq;
import com.tracci.module.trip.dto.response.ItineraryNodeResp;
import com.tracci.module.trip.dto.response.ItineraryResp;

public interface ItineraryService {

    ItineraryResp getItinerary(String tripId);

    ItineraryNodeResp createNode(String tripId, String dayId, ItineraryNodeCreateReq req);

    ItineraryNodeResp updateNode(String tripId, String dayId, String nodeId, ItineraryNodeUpdateReq req);

    void deleteNode(String tripId, String dayId, String nodeId);

    void reorderNodes(String tripId, String dayId, ItineraryNodeReorderReq req);

    /**
     * 旅程日期变更后：裁剪/同步 hotel Stay Group（删除越界投影、校正 checkIn/checkOutDate）。
     * 由 {@link TripServiceImpl#syncTripDays} 在改期后调用。
     */
    void reconcileHotelStaysAfterTripDateChange(String tripId);
}
