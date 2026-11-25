package com.tracci.module.user.support;

import com.tracci.module.user.entity.User;
import org.springframework.util.StringUtils;

public final class UserDisplaySupport {

    private static final String DELETED_USER_NICKNAME = "已注销用户";
    private static final String DEFAULT_NICKNAME = "旅人";
    private static final String ACTIVE_ACCOUNT_STATUS = "active";
    private static final String DELETED_ACCOUNT_STATUS = "deleted";

    private UserDisplaySupport() {
    }

    public static String nickname(User user) {
        if (user == null) {
            return DELETED_USER_NICKNAME;
        }
        return StringUtils.hasText(user.getNickname()) ? user.getNickname() : DEFAULT_NICKNAME;
    }

    public static String accountStatus(User user) {
        if (user == null) {
            return DELETED_ACCOUNT_STATUS;
        }
        return StringUtils.hasText(user.getStatus()) ? user.getStatus() : ACTIVE_ACCOUNT_STATUS;
    }
}
