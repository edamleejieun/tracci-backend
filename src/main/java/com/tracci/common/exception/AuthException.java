package com.tracci.common.exception;

import com.tracci.common.response.ErrorCode;

/**
 * 认证 / 授权异常（401）：未登录、Token 失效
 */
public class AuthException extends BizException {

    public AuthException(ErrorCode errorCode) {
        super(errorCode);
    }

    public AuthException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
