package com.tracci.common.exception;

import com.tracci.common.response.ErrorCode;

/**
 * 数据归属异常（403）：当前用户无权限操作该资源
 */
public class ForbiddenException extends BizException {

    public ForbiddenException() {
        super(ErrorCode.FORBIDDEN);
    }

    public ForbiddenException(String message) {
        super(ErrorCode.FORBIDDEN, message);
    }
}
