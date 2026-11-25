package com.tracci.module.placecontent.service;

import com.tracci.module.file.dto.request.ConfirmUploadReq;
import com.tracci.module.file.dto.request.UploadCredentialReq;
import com.tracci.module.file.dto.response.UploadCredentialResp;
import com.tracci.module.placecontent.dto.request.PlaceMediaUpdateRequest;
import com.tracci.module.placecontent.dto.response.PlaceMediaAdminItem;

import java.util.List;

/**
 * 管理端地点媒体：上传凭证、确认落库、列表、设封面、归档。
 * 不走用户文件 API，objectKey 使用 places/{placeId}/media/...
 */
public interface PlaceMediaAdminService {

    UploadCredentialResp createUploadCredential(String placeId, UploadCredentialReq request);

    PlaceMediaAdminItem confirmUpload(String placeId, String fileId, ConfirmUploadReq request);

    List<PlaceMediaAdminItem> listMedia(String placeId);

    PlaceMediaAdminItem updateMedia(String mediaId, PlaceMediaUpdateRequest request);

    PlaceMediaAdminItem setCover(String mediaId);

    void archiveMedia(String mediaId);
}
