package com.tracci.common.exception;

import com.tracci.common.response.ErrorCode;

/**
 * 资源不存在异常（404）
 */
public class NotFoundException extends BizException {

    public NotFoundException(ErrorCode errorCode) {
        super(errorCode);
    }

    public NotFoundException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
