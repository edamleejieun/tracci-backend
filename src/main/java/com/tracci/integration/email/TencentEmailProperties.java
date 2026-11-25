package com.tracci.integration.email;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 腾讯云邮件推送（SES）配置属性。
 * <p>
 * 配置前缀：{@code tracci.integration.email}
 * <p>
 * 模板变量与 {@code docs/email-templates/reset-password.html} 对齐，当前使用 {@code code}。
 */
@Getter
@Setter
@Component
@ConfigurationProperties("tracci.integration.email")
public class TencentEmailProperties {

    /** 是否使用 Mock 模式（开发/测试环境） */
    private boolean mock = true;

    /** 腾讯云 SecretId */
    private String secretId;

    /** 腾讯云 SecretKey */
    private String secretKey;

    /**
     * 接入地域，SES 仅支持 ap-guangzhou、ap-hongkong。
     *
     * @see <a href="https://cloud.tencent.com/document/product/1288/51034">SendEmail</a>
     */
    private String region = "ap-guangzhou";

    /** 已验证的发信地址，如 noreply@mail.example.com */
    private String fromEmail;

    /** 发信人别名，展示为「别名 &lt;邮箱&gt;」；为空则仅使用 fromEmail */
    private String fromName = "趣溪";

    /** 回复地址（可选） */
    private String replyTo;

    /**
     * 邮件触发类型：0 非触发类，1 触发类（验证码等即时邮件）。
     */
    private long triggerType = 1L;

    /** 找回密码模板 ID（腾讯云控制台审核通过后填写） */
    private String resetPasswordTemplateId;

    /** 找回密码邮件主题 */
    private String resetPasswordSubject = "【趣溪】找回密码验证码";

    /** 手机号一键登录账号首次绑定邮箱模板 ID */
    private String bindEmailTemplateId;

    /** 首次绑定邮箱邮件主题 */
    private String bindEmailSubject = "【趣溪】绑定邮箱验证码";

}
