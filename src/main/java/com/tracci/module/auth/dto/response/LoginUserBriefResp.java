package com.tracci.module.auth.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 登录/注册成功后返回的用户简要信息
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LoginUserBriefResp(
        String id,
        String email,
        String nickname,
        String avatarFileId,
        String avatarUrl,
        String loginType
) {
}
