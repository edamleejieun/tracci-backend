package com.tracci.common.exception;

import com.tracci.common.response.ErrorCode;
import lombok.Getter;

/**
 * 业务异常基类，所有可预期的业务错误均继承此类
 */
@Getter
public class BizException extends RuntimeException {

    private final ErrorCode errorCode;

    public BizException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public BizException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}
