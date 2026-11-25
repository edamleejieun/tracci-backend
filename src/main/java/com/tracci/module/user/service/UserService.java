package com.tracci.module.user.service;

import com.tracci.module.user.dto.request.DeleteAccountReq;
import com.tracci.module.user.dto.request.UserUpdateReq;
import com.tracci.module.user.dto.response.UserProfileResp;
import com.tracci.module.user.dto.response.UserSecurityResp;

public interface UserService {

    UserProfileResp getMe();

    UserProfileResp updateMe(UserUpdateReq req);

    /**
     * 账号安全状态（邮箱/手机/绑定情况）
     */
    UserSecurityResp getSecurity();

    void deleteAccount(DeleteAccountReq req);
}
