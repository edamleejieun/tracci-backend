package com.tracci.module.export.service;

import com.tracci.module.export.dto.response.ExportAssetsResp;
import com.tracci.module.export.dto.response.ItineraryExportAssetsResp;

public interface ExportService {

    ExportAssetsResp getExportAssets(String tripId, String template, String format);

    ItineraryExportAssetsResp getItineraryExportAssets(String tripId, String dates);
}
