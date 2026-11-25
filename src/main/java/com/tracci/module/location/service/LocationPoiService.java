package com.tracci.module.location.service;

import com.tracci.module.location.dto.request.LocationPoiSearchReq;
import com.tracci.module.location.dto.response.LocationPoiSearchResp;

public interface LocationPoiService {

    LocationPoiSearchResp search(LocationPoiSearchReq req);
}
