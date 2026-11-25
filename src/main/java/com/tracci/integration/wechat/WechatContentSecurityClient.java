package com.tracci.integration.wechat;

public interface WechatContentSecurityClient {

    void checkText(String openid, String content);

    void checkImage(String openid, byte[] imageBytes);
}
