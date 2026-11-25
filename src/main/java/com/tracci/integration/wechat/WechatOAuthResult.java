package com.tracci.integration.wechat;

/**
 * 微信开放平台 OAuth 授权结果（iOS App 登录）
 */
public record WechatOAuthResult(
        String openid,
        String unionid,
        String nickname,
        String avatarUrl) {
}
