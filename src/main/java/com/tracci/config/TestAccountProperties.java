package com.tracci.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 测试账号白名单配置。
 * <p>
 * dev/test 默认开启，prod 默认关闭。admin 账号通过此配置注册，
 * 不影响正式邮箱账号规则。
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "tracci.auth.test-accounts")
public class TestAccountProperties {

    /** 是否启用测试账号白名单 */
    private boolean enabled = false;

    /** 测试账号列表 */
    private List<Account> accounts = new ArrayList<>();

    @Getter
    @Setter
    public static class Account {
        /** 登录标识（如 admin） */
        private String loginId;
        /** 登录密码 */
        private String password;
        /** 对应数据库中的邮箱（如 admin@tracci.local） */
        private String email;
    }
}
