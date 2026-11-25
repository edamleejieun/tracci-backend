package com.tracci.common.exception;

import com.tracci.common.response.ErrorCode;

/**
 * 业务层参数校验异常（422）：区别于 @Valid 的 DTO 校验
 * 用于业务规则中的数据合法性校验，例如"日志最多 9 张照片"
 */
public class ValidationException extends BizException {

    public ValidationException(ErrorCode errorCode) {
        super(errorCode);
    }

    public ValidationException(String message) {
        super(ErrorCode.PARAM_INVALID, message);
    }
}
