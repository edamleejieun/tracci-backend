package com.tracci.module.auth.dto.response;

/**
 * 使用 RefreshToken 换取的新 AccessToken（不含 refreshToken 本体）
 */
public record AccessTokenRefreshResp(
        String accessToken,
        String accessTokenExpiresAt
) {
}
