package com.tracci.module.auth.dto.response;

public record WechatMiniLoginResp(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn,
        String accessTokenExpiresAt,
        LoginUserBriefResp user,
        boolean isNewUser
) {
}
