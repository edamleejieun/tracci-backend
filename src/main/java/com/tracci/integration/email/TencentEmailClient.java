package com.tracci.integration.email;

import com.tencentcloudapi.common.Credential;
import com.tencentcloudapi.common.exception.TencentCloudSDKException;
import com.tencentcloudapi.common.profile.ClientProfile;
import com.tencentcloudapi.common.profile.HttpProfile;
import com.tencentcloudapi.ses.v20201002.models.SendEmailRequest;
import com.tencentcloudapi.ses.v20201002.models.SendEmailResponse;
import com.tencentcloudapi.ses.v20201002.models.Template;
import com.tracci.common.exception.ThirdPartyException;
import com.tracci.common.response.ErrorCode;
import com.tracci.module.auth.enums.EmailCodePurpose;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

/**
 * 腾讯云邮件推送（SES）客户端，使用模板发送验证码邮件。
 *
 * @see <a href="https://cloud.tencent.com/document/product/1288/51034">SendEmail</a>
 */
@Slf4j
public class TencentEmailClient implements EmailClient {

    private final TencentEmailProperties properties;
    private final com.tencentcloudapi.ses.v20201002.SesClient sesClient;

    public TencentEmailClient(TencentEmailProperties properties) {
        this.properties = properties;

        Credential cred = new Credential(properties.getSecretId(), properties.getSecretKey());

        HttpProfile httpProfile = new HttpProfile();
        httpProfile.setEndpoint("ses.tencentcloudapi.com");
        httpProfile.setConnTimeout(10);
        httpProfile.setReadTimeout(10);

        ClientProfile clientProfile = new ClientProfile();
        clientProfile.setHttpProfile(httpProfile);

        this.sesClient = new com.tencentcloudapi.ses.v20201002.SesClient(
                cred, properties.getRegion(), clientProfile);
    }

    @Override
    public void sendVerificationCode(String toEmail, String code, EmailCodePurpose purpose) {
        TemplateConfig templateConfig = resolveTemplateConfig(purpose);

        SendEmailRequest req = new SendEmailRequest();
        req.setFromEmailAddress(buildFromEmailAddress());
        req.setSubject(templateConfig.subject());
        req.setDestination(new String[]{toEmail});
        req.setTriggerType(properties.getTriggerType());

        if (StringUtils.hasText(properties.getReplyTo())) {
            req.setReplyToAddresses(properties.getReplyTo().trim());
        }

        Template template = new Template();
        template.setTemplateID(templateConfig.templateId());
        template.setTemplateData(buildTemplateData(code));
        req.setTemplate(template);

        try {
            SendEmailResponse resp = sesClient.SendEmail(req);
            log.info("Tencent SES sent email to={} purpose={} messageId={} requestId={}",
                    toEmail, purpose, resp.getMessageId(), resp.getRequestId());
        } catch (TencentCloudSDKException e) {
            log.error("Tencent SES SDK exception to={} purpose={} errorCode={}",
                    toEmail, purpose, e.getErrorCode(), e);
            throw new ThirdPartyException(ErrorCode.EMAIL_SEND_FAILED, "邮件发送失败，请稍后重试");
        }
    }

    TemplateConfig resolveTemplateConfig(EmailCodePurpose purpose) {
        return switch (purpose) {
            case RESET_PASSWORD -> new TemplateConfig(
                    parseTemplateId(properties.getResetPasswordTemplateId(), "reset-password-template-id"),
                    properties.getResetPasswordSubject());
            case BIND_EMAIL -> new TemplateConfig(
                    parseTemplateId(properties.getBindEmailTemplateId(), "bind-email-template-id"),
                    properties.getBindEmailSubject());
        };
    }

    private long parseTemplateId(String templateId, String propertyName) {
        if (!StringUtils.hasText(templateId)) {
            throw new IllegalStateException(
                    "tracci.integration.email." + propertyName + " must be configured when email.mock=false");
        }
        try {
            return Long.parseLong(templateId.trim());
        } catch (NumberFormatException ex) {
            throw new IllegalStateException(
                    "tracci.integration.email." + propertyName + " must be a numeric template id");
        }
    }

    private String buildFromEmailAddress() {
        String fromEmail = properties.getFromEmail().trim();
        String fromName = properties.getFromName();
        if (StringUtils.hasText(fromName)) {
            fromName = fromName.trim().replace(":", "");
            if (!fromName.equals(properties.getFromName().trim())) {
                log.warn("Removed colon(s) from email fromName='{}' → '{}'",
                        properties.getFromName(), fromName);
            }
        }
        String result = StringUtils.hasText(fromName)
                ? fromName + " <" + fromEmail + ">"
                : fromEmail;
        log.info("Email from address built: {}", result);
        return result;
    }

    String buildTemplateData(String code) {
        return "{\"code\":\"" + code + "\"}";
    }

    record TemplateConfig(long templateId, String subject) {
    }
}
