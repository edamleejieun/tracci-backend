package com.tracci.module.file.controller;

import com.tracci.common.response.ApiResponse;
import com.tracci.module.file.dto.request.ConfirmUploadReq;
import com.tracci.module.file.dto.request.UploadCredentialReq;
import com.tracci.module.file.dto.response.AccessUrlResp;
import com.tracci.module.file.dto.response.FileResp;
import com.tracci.module.file.dto.response.UploadCredentialResp;
import com.tracci.module.file.service.FileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/files")
@RequiredArgsConstructor
public class FileController {

    private final FileService fileService;

    @PostMapping("/upload-credentials")
    public ApiResponse<UploadCredentialResp> createUploadCredential(
            @Valid @RequestBody UploadCredentialReq req) {
        return ApiResponse.ok(fileService.createUploadCredential(req));
    }

    @PostMapping("/{fileId}/confirm")
    public ApiResponse<FileResp> confirmUpload(
            @PathVariable String fileId,
            @Valid @RequestBody ConfirmUploadReq req) {
        return ApiResponse.ok(fileService.confirmUpload(fileId, req));
    }

    @GetMapping("/{fileId}/access-url")
    public ApiResponse<AccessUrlResp> getAccessUrl(@PathVariable String fileId) {
        return ApiResponse.ok(fileService.getAccessUrl(fileId));
    }

    @DeleteMapping("/{fileId}")
    public ApiResponse<Void> deleteFile(@PathVariable String fileId) {
        fileService.deleteFile(fileId);
        return ApiResponse.ok();
    }
}
