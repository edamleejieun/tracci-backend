package com.tracci.integration.wechat;

/**
 * 微信小程序 jscode2session 适配
 */
public interface WechatMiniClient {

    WechatSession exchangeCode(String code);
}
