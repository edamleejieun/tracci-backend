package com.tracci.module.placecontent.controller;

import com.tracci.common.context.LoginUserContext;
import com.tracci.common.exception.ForbiddenException;
import com.tracci.common.response.ApiResponse;
import com.tracci.module.file.dto.request.ConfirmUploadReq;
import com.tracci.module.file.dto.request.UploadCredentialReq;
import com.tracci.module.file.dto.response.UploadCredentialResp;
import com.tracci.module.placecontent.dto.request.PlaceMediaUpdateRequest;
import com.tracci.module.placecontent.dto.response.PlaceMediaAdminItem;
import com.tracci.module.placecontent.service.PlaceMediaAdminService;
import com.tracci.service.AdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/internal/v1")
@RequiredArgsConstructor
public class PlaceMediaAdminController {

    private final PlaceMediaAdminService placeMediaAdminService;
    private final AdminService adminService;

    @PostMapping("/places/{placeId}/media/upload-credentials")
    public ApiResponse<UploadCredentialResp> createUploadCredential(
            @PathVariable String placeId,
            @Valid @RequestBody UploadCredentialReq request) {
        requireAdmin();
        return ApiResponse.ok(placeMediaAdminService.createUploadCredential(placeId, request));
    }

    @PostMapping("/places/{placeId}/media/{fileId}/confirm")
    public ApiResponse<PlaceMediaAdminItem> confirmUpload(
            @PathVariable String placeId,
            @PathVariable String fileId,
            @RequestBody(required = false) ConfirmUploadReq request) {
        requireAdmin();
        return ApiResponse.ok(placeMediaAdminService.confirmUpload(
                placeId, fileId, request == null ? new ConfirmUploadReq(null, null, null) : request));
    }

    @GetMapping("/places/{placeId}/media")
    public ApiResponse<List<PlaceMediaAdminItem>> listMedia(@PathVariable String placeId) {
        requireAdmin();
        return ApiResponse.ok(placeMediaAdminService.listMedia(placeId));
    }

    @PutMapping("/place-media/{mediaId}")
    public ApiResponse<PlaceMediaAdminItem> updateMedia(
            @PathVariable String mediaId,
            @Valid @RequestBody PlaceMediaUpdateRequest request) {
        requireAdmin();
        return ApiResponse.ok(placeMediaAdminService.updateMedia(mediaId, request));
    }

    @PostMapping("/place-media/{mediaId}/cover")
    public ApiResponse<PlaceMediaAdminItem> setCover(@PathVariable String mediaId) {
        requireAdmin();
        return ApiResponse.ok(placeMediaAdminService.setCover(mediaId));
    }

    @PostMapping("/place-media/{mediaId}/archive")
    public ApiResponse<Void> archiveMedia(@PathVariable String mediaId) {
        requireAdmin();
        placeMediaAdminService.archiveMedia(mediaId);
        return ApiResponse.ok();
    }

    private void requireAdmin() {
        LoginUserContext.requireUserId();
        if (!adminService.isCurrentUserAdmin()) {
            throw new ForbiddenException("仅管理员可管理地点媒体");
        }
    }
}
