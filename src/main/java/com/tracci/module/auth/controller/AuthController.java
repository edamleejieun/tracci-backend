package com.tracci.module.auth.controller;

import com.tracci.common.annotation.NoAuth;
import com.tracci.common.response.ApiResponse;
import com.tracci.module.auth.dto.request.ChangePasswordReq;
import com.tracci.module.auth.dto.request.EmailBindConfirmReq;
import com.tracci.module.auth.dto.request.EmailBindSendCodeReq;
import com.tracci.module.auth.dto.request.LoginReq;
import com.tracci.module.auth.dto.request.PhoneBindConfirmReq;
import com.tracci.module.auth.dto.request.PhoneBindSendCodeReq;
import com.tracci.module.auth.dto.request.PhoneOneTapLoginReq;
import com.tracci.module.auth.dto.request.RefreshTokenReq;
import com.tracci.module.auth.dto.request.RegisterReq;
import com.tracci.module.auth.dto.request.ResetPasswordReq;
import com.tracci.module.auth.dto.request.SendResetPasswordEmailCodeReq;
import com.tracci.module.auth.dto.request.WechatAppLoginReq;
import com.tracci.module.auth.dto.request.WechatMiniLoginReq;
import com.tracci.module.auth.dto.response.AccessTokenRefreshResp;
import com.tracci.module.auth.dto.response.EmailCodeSendResp;
import com.tracci.module.auth.dto.response.SmsSendResp;
import com.tracci.module.auth.dto.response.TokenPairResp;
import com.tracci.module.auth.dto.response.WechatMiniLoginResp;
import com.tracci.module.auth.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    @NoAuth
    public ApiResponse<TokenPairResp> register(@Valid @RequestBody RegisterReq req) {
        return ApiResponse.ok(authService.register(req));
    }

    @PostMapping("/login")
    @NoAuth
    public ApiResponse<TokenPairResp> login(@Valid @RequestBody LoginReq req) {
        return ApiResponse.ok(authService.login(req));
    }

    @PostMapping("/password/change")
    public ApiResponse<Void> changePassword(@Valid @RequestBody ChangePasswordReq req) {
        authService.changePassword(req);
        return ApiResponse.ok();
    }

    @PostMapping("/password/reset/email-code")
    @NoAuth
    public ApiResponse<EmailCodeSendResp> sendResetPasswordEmailCode(
            @Valid @RequestBody SendResetPasswordEmailCodeReq req) {
        return ApiResponse.ok(authService.sendResetPasswordEmailCode(req));
    }

    @PostMapping("/password/reset")
    @NoAuth
    public ApiResponse<Void> resetPasswordByEmailCode(@Valid @RequestBody ResetPasswordReq req) {
        authService.resetPasswordByEmailCode(req);
        return ApiResponse.ok();
    }

    @PostMapping("/email/bind/send-code")
    public ApiResponse<EmailCodeSendResp> sendEmailBindCode(@Valid @RequestBody EmailBindSendCodeReq req) {
        return ApiResponse.ok(authService.sendEmailBindCode(req));
    }

    @PostMapping("/email/bind/confirm")
    public ApiResponse<Void> confirmEmailBind(@Valid @RequestBody EmailBindConfirmReq req) {
        authService.confirmEmailBind(req);
        return ApiResponse.ok();
    }

    /*
     * FUTURE（待产品确认）：邮箱换绑入口暂不开放。
     *
     * 不直接复用首次绑定接口放宽限制。正式实施前需先确认：
     * 1. 是否校验当前密码或原邮箱验证码；
     * 2. 新邮箱验证码与重放/并发策略；
     * 3. EMAIL identity 原子替换及登录会话撤销规则；
     * 4. 独立 API 路径与跨端交互契约。
     *
     * 确认后再在此处恢复 Controller 入口。
     */

    @PostMapping("/phone/one-tap-login")
    @NoAuth
    public ApiResponse<TokenPairResp> phoneOneTapLogin(@Valid @RequestBody PhoneOneTapLoginReq req) {
        return ApiResponse.ok(authService.phoneOneTapLogin(req));
    }

    // -----------------------------------------------------------------------
    // 手机号绑定/解绑
    // -----------------------------------------------------------------------

    @PostMapping("/phone/bind/send-code")
    public ApiResponse<SmsSendResp> sendPhoneBindCode(@Valid @RequestBody PhoneBindSendCodeReq req) {
        return ApiResponse.ok(authService.sendPhoneBindCode(req));
    }

    @PostMapping("/phone/bind/confirm")
    public ApiResponse<Void> confirmPhoneBind(@Valid @RequestBody PhoneBindConfirmReq req) {
        authService.confirmPhoneBind(req);
        return ApiResponse.ok();
    }

    @PostMapping("/phone/unbind")
    public ApiResponse<Void> unbindPhone() {
        authService.unbindPhone();
        return ApiResponse.ok();
    }

    // -----------------------------------------------------------------------
    // 微信登录：小程序 / iOS App 使用独立 feature flag 控制。
    // 开启后可正常登录，并通过 unionid 实现微信体系内跨端互通。
    // -----------------------------------------------------------------------

    @PostMapping("/wechat-mini/login")
    @NoAuth
    public ApiResponse<WechatMiniLoginResp> wechatMiniLogin(@Valid @RequestBody WechatMiniLoginReq req) {
        return ApiResponse.ok(authService.wechatMiniLogin(req));
    }

    @PostMapping("/wechat-app/login")
    @NoAuth
    public ApiResponse<WechatMiniLoginResp> wechatAppLogin(@Valid @RequestBody WechatAppLoginReq req) {
        return ApiResponse.ok(authService.wechatAppLogin(req));
    }

    @PostMapping("/refresh")
    @NoAuth
    public ApiResponse<AccessTokenRefreshResp> refresh(@Valid @RequestBody RefreshTokenReq req) {
        return ApiResponse.ok(authService.refresh(req));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout() {
        authService.logout();
        return ApiResponse.ok();
    }
}
