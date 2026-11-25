package com.tracci.module.auth.service;

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

public interface AuthService {

    TokenPairResp register(RegisterReq req);

    TokenPairResp login(LoginReq req);

    void changePassword(ChangePasswordReq req);

    /**
     * 发送重置密码邮箱验证码（无需登录）
     */
    EmailCodeSendResp sendResetPasswordEmailCode(SendResetPasswordEmailCodeReq req);

    /**
     * 通过邮箱验证码重置密码（无需登录）
     */
    void resetPasswordByEmailCode(ResetPasswordReq req);

    /**
     * 手机号一键登录账号发送邮箱绑定验证码（需登录）
     */
    EmailCodeSendResp sendEmailBindCode(EmailBindSendCodeReq req);

    /**
     * 手机号一键登录账号确认绑定邮箱并设置邮箱登录密码（需登录）
     */
    void confirmEmailBind(EmailBindConfirmReq req);

    /*
     * FUTURE（待产品确认）：邮箱换绑 Service 契约。
     * 当前不声明可调用方法，避免首次绑定逻辑被误用为换绑。
     * 产品与安全方案确认后，再补充独立请求 DTO、发送验证码及确认换绑方法。
     */

    TokenPairResp phoneOneTapLogin(PhoneOneTapLoginReq req);

    WechatMiniLoginResp wechatMiniLogin(WechatMiniLoginReq req);

    WechatMiniLoginResp wechatAppLogin(WechatAppLoginReq req);

    /**
     * 发送手机号绑定验证码（需登录）
     */
    SmsSendResp sendPhoneBindCode(PhoneBindSendCodeReq req);

    /**
     * 确认绑定手机号（需登录）
     */
    void confirmPhoneBind(PhoneBindConfirmReq req);

    /**
     * 解绑手机号（需登录）
     */
    void unbindPhone();

    AccessTokenRefreshResp refresh(RefreshTokenReq req);

    void logout();
}
