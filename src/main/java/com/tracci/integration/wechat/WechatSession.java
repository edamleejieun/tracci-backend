package com.tracci.integration.wechat;

/**
 * 微信 code2session 结果
 */
public record WechatSession(String openid, String sessionKey, String unionid) {
}
