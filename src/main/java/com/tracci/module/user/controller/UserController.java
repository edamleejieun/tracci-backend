package com.tracci.module.user.controller;

import com.tracci.common.response.ApiResponse;
import com.tracci.common.response.ErrorCode;
import com.tracci.module.user.dto.request.DeleteAccountReq;
import com.tracci.module.user.dto.request.UserUpdateReq;
import com.tracci.module.user.dto.response.UserProfileResp;
import com.tracci.module.user.dto.response.UserSecurityResp;
import com.tracci.module.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public ApiResponse<UserProfileResp> me() {
        return ApiResponse.ok(userService.getMe());
    }

    @PutMapping("/me")
    public ApiResponse<UserProfileResp> updateMe(@Valid @RequestBody UserUpdateReq req) {
        return ApiResponse.ok(userService.updateMe(req));
    }

    @GetMapping("/me/security")
    public ApiResponse<UserSecurityResp> security() {
        return ApiResponse.ok(userService.getSecurity());
    }

    @PostMapping("/me/delete-account")
    public ApiResponse<Void> deleteAccount(@Valid @RequestBody DeleteAccountReq req) {
        userService.deleteAccount(req);
        return new ApiResponse<>(ErrorCode.SUCCESS.getCode(), "账号已注销", null);
    }
}
