package com.tracci.module.summary.service;

import com.tracci.module.summary.dto.response.SummaryResp;

public interface SummaryService {

    SummaryResp getSummary(String tripId);
}
