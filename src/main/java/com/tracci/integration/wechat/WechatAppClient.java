package com.tracci.integration.wechat;

/**
 * 微信开放平台 App OAuth 适配（iOS 微信 SDK 授权 code）
 */
public interface WechatAppClient {

    WechatOAuthResult exchangeCode(String code);
}
