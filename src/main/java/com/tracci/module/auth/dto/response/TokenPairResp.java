package com.tracci.module.auth.dto.response;

/**
 * 登录 / 注册成功：双 Token
 */
public record TokenPairResp(
        String accessToken,
        String refreshToken,
        String accessTokenExpiresAt,
        LoginUserBriefResp user,
        boolean isNewUser
) {
}
