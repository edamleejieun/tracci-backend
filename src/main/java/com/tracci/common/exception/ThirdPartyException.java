package com.tracci.common.exception;

import com.tracci.common.response.ErrorCode;

/**
 * 第三方服务调用失败异常（502）：微信、阿里云短信、OSS、汇率 API 等
 */
public class ThirdPartyException extends BizException {

    public ThirdPartyException(ErrorCode errorCode) {
        super(errorCode);
    }

    public ThirdPartyException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
