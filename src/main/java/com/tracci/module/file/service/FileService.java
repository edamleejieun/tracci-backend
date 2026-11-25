package com.tracci.module.file.service;

import com.tracci.module.file.dto.request.ConfirmUploadReq;
import com.tracci.module.file.dto.request.UploadCredentialReq;
import com.tracci.module.file.dto.response.AccessUrlResp;
import com.tracci.module.file.dto.response.FileResp;
import com.tracci.module.file.dto.response.UploadCredentialResp;

public interface FileService {

    UploadCredentialResp createUploadCredential(UploadCredentialReq req);

    FileResp confirmUpload(String fileId, ConfirmUploadReq req);

    AccessUrlResp getAccessUrl(String fileId);

    void deleteFile(String fileId);
}
