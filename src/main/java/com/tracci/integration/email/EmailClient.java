package com.tracci.integration.email;

import com.tracci.module.auth.enums.EmailCodePurpose;

/**
 * 邮件发送客户端抽象。
 * <p>
 * dev/test 使用 MockEmailClient（日志打印验证码），
 * prod 使用真实邮件服务实现（如腾讯云邮件推送）。
 */
public interface EmailClient {

    /**
     * 发送验证码邮件
     *
     * @param toEmail 收件人邮箱
     * @param code    6 位数字验证码
     * @param purpose 验证码用途
     */
    void sendVerificationCode(String toEmail, String code, EmailCodePurpose purpose);
}
