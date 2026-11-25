package com.tracci.integration.sms;

import com.aliyun.dypnsapi20170525.models.CheckSmsVerifyCodeRequest;
import com.aliyun.dypnsapi20170525.models.CheckSmsVerifyCodeResponse;
import com.aliyun.dypnsapi20170525.models.CheckSmsVerifyCodeResponseBody;
import com.aliyun.dypnsapi20170525.models.GetMobileRequest;
import com.aliyun.dypnsapi20170525.models.GetMobileResponse;
import com.aliyun.dypnsapi20170525.models.GetMobileResponseBody;
import com.aliyun.dypnsapi20170525.models.SendSmsVerifyCodeRequest;
import com.aliyun.dypnsapi20170525.models.SendSmsVerifyCodeResponse;
import com.aliyun.dypnsapi20170525.models.SendSmsVerifyCodeResponseBody;
import com.aliyun.teaopenapi.models.Config;
import com.tracci.common.exception.BizException;
import com.tracci.common.exception.ThirdPartyException;
import com.tracci.common.response.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

@Slf4j
public class AliyunPnvsSmsClient implements SmsClient {

    private static final String ALIYUN_SUCCESS_CODE = "OK";
    private static final String VERIFY_PASS = "PASS";

    private final AliyunPnvsProperties properties;
    private final com.aliyun.dypnsapi20170525.Client client;

    public AliyunPnvsSmsClient(AliyunPnvsProperties properties) {
        this.properties = properties;
        try {
            Config config = new Config()
                    .setAccessKeyId(properties.getAccessKeyId())
                    .setAccessKeySecret(properties.getAccessKeySecret())
                    .setEndpoint(properties.getEndpoint())
                    .setRegionId(properties.getRegionId());
            this.client = new com.aliyun.dypnsapi20170525.Client(config);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to initialize Aliyun PNVS client", ex);
        }
    }

    @Override
    public void sendVerificationCode(String phone, String ignoredCode) {
        SendSmsVerifyCodeRequest request = buildSendVerificationCodeRequest(phone);

        try {
            SendSmsVerifyCodeResponse response = client.sendSmsVerifyCode(request);
            SendSmsVerifyCodeResponseBody body = response.getBody();
            if (body == null || !Boolean.TRUE.equals(body.getSuccess())) {
                log.error("Aliyun PNVS send SMS failed phone={} code={} message={} requestId={}",
                        phone,
                        body == null ? null : body.getCode(),
                        body == null ? null : body.getMessage(),
                        body == null ? null : body.getRequestId());
                throw new ThirdPartyException(ErrorCode.SMS_SEND_FAILED, "短信发送失败，请稍后重试");
            }
            log.info("Aliyun PNVS SMS sent phone={} requestId={}", phone, body.getRequestId());
        } catch (BizException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Aliyun PNVS send SMS exception phone={}", phone, ex);
            throw new ThirdPartyException(ErrorCode.SMS_SEND_FAILED, "短信发送失败，请稍后重试");
        }
    }

    SendSmsVerifyCodeRequest buildSendVerificationCodeRequest(String phone) {
        SendSmsVerifyCodeRequest request = new SendSmsVerifyCodeRequest()
                .setCountryCode(properties.getCountryCode())
                .setPhoneNumber(toMainlandPhone(phone))
                .setSignName(properties.getSignName())
                .setTemplateCode(properties.getTemplateCode())
                .setTemplateParam(properties.getTemplateParam())
                .setCodeLength(properties.getCodeLength())
                .setValidTime(properties.getValidTimeSeconds())
                .setInterval(properties.getIntervalSeconds());
        if (StringUtils.hasText(properties.getSchemeName())) {
            request.setSchemeName(properties.getSchemeName());
        }
        return request;
    }

    @Override
    public boolean storesVerificationCodeLocally() {
        return false;
    }

    @Override
    public boolean checkVerificationCode(String phone, String code) {
        CheckSmsVerifyCodeRequest request = new CheckSmsVerifyCodeRequest()
                .setCountryCode(properties.getCountryCode())
                .setPhoneNumber(toMainlandPhone(phone))
                .setVerifyCode(code);
        if (StringUtils.hasText(properties.getSchemeName())) {
            request.setSchemeName(properties.getSchemeName());
        }

        try {
            CheckSmsVerifyCodeResponse response = client.checkSmsVerifyCode(request);
            CheckSmsVerifyCodeResponseBody body = response.getBody();
            String verifyResult = body != null && body.getModel() != null
                    ? body.getModel().getVerifyResult()
                    : null;
            boolean passed = Boolean.TRUE.equals(body == null ? null : body.getSuccess())
                    && VERIFY_PASS.equalsIgnoreCase(verifyResult);
            if (!passed) {
                log.warn("Aliyun PNVS SMS verify failed phone={} code={} verifyResult={} message={}",
                        phone,
                        body == null ? null : body.getCode(),
                        verifyResult,
                        body == null ? null : body.getMessage());
            }
            return passed;
        } catch (Exception ex) {
            log.error("Aliyun PNVS SMS verify exception phone={}", phone, ex);
            throw new ThirdPartyException(ErrorCode.SMS_SEND_FAILED, "短信校验失败，请稍后重试");
        }
    }

    @Override
    public String getMobileByAccessToken(String accessToken) {
        try {
            GetMobileResponse response = client.getMobile(new GetMobileRequest().setAccessToken(accessToken));
            GetMobileResponseBody body = response.getBody();
            if (body == null || !ALIYUN_SUCCESS_CODE.equalsIgnoreCase(body.getCode())
                    || body.getGetMobileResultDTO() == null
                    || !StringUtils.hasText(body.getGetMobileResultDTO().getMobile())) {
                log.error("Aliyun PNVS get mobile failed code={} message={} requestId={}",
                        body == null ? null : body.getCode(),
                        body == null ? null : body.getMessage(),
                        body == null ? null : body.getRequestId());
                throw new ThirdPartyException(ErrorCode.SMS_SEND_FAILED, "本机号码认证失败，请重试");
            }
            return toStoredPhone(body.getGetMobileResultDTO().getMobile());
        } catch (BizException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Aliyun PNVS get mobile exception", ex);
            throw new ThirdPartyException(ErrorCode.SMS_SEND_FAILED, "本机号码认证失败，请重试");
        }
    }

    private String toMainlandPhone(String phone) {
        if (phone != null && phone.startsWith("+86")) {
            return phone.substring(3);
        }
        return phone;
    }

    private String toStoredPhone(String mobile) {
        String trimmed = mobile.trim();
        if (trimmed.startsWith("+86")) {
            return trimmed;
        }
        if (trimmed.startsWith("86") && trimmed.length() == 13) {
            return "+" + trimmed;
        }
        return "+86" + trimmed;
    }
}
