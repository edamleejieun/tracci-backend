package com.tracci.common.context;

import com.tracci.common.exception.AuthException;
import com.tracci.common.response.ErrorCode;

/**
 * 当前登录用户上下文（基于请求线程的 ThreadLocal）。
 * <p>
 * 规则约定：业务层从本类读取用户 ID，禁止依赖前端传入的 userId 决定数据归属。
 */
public final class LoginUserContext {

    private static final ThreadLocal<String> CURRENT_USER_ID = new ThreadLocal<>();

    private LoginUserContext() {
    }

    public static void setUserId(String userId) {
        CURRENT_USER_ID.set(userId);
    }

    public static String getUserIdOrNull() {
        return CURRENT_USER_ID.get();
    }

    /**
     * 必须由已鉴权请求调用；否则抛出 {@link AuthException}（未登录）
     */
    public static String requireUserId() {
        String id = CURRENT_USER_ID.get();
        if (id == null || id.isBlank()) {
            throw new AuthException(ErrorCode.TOKEN_MISSING);
        }
        return id;
    }

    public static void clear() {
        CURRENT_USER_ID.remove();
    }
}
