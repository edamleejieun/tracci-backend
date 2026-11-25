package com.tracci.common.support;

import com.tracci.common.exception.BizException;
import com.tracci.common.response.ErrorCode;
import com.tracci.module.user.entity.User;

/**
 * 用户账号状态约定与鉴权校验。
 */
public final class UserAccountStatus {

    public static final String ACTIVE = "active";
    public static final String DISABLED = "disabled";
    public static final String DELETED = "deleted";

    private UserAccountStatus() {
    }

    public static boolean isActive(User user) {
        return user != null && ACTIVE.equalsIgnoreCase(user.getStatus());
    }

    public static boolean isDeleted(User user) {
        return user != null && DELETED.equalsIgnoreCase(user.getStatus());
    }

    /**
     * 登录、刷新、资料等需要「可用账号」的场景。
     */
    public static void requireActive(User user) {
        if (user == null) {
            throw new BizException(ErrorCode.USER_NOT_FOUND);
        }
        if (isActive(user)) {
            return;
        }
        if (isDeleted(user)) {
            throw new BizException(ErrorCode.ACCOUNT_DELETED);
        }
        throw new BizException(ErrorCode.FORBIDDEN, "账号已禁用");
    }
}
