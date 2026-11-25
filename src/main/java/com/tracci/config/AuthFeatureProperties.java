package com.tracci.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 认证功能配置。
 * <p>
 * 登录方式（微信、手机号等）始终可用，由前端根据平台控制展示。
 * 此配置仅保留需要后端控制的功能开关。
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "tracci.auth.features")
public class AuthFeatureProperties {

    /** 邮箱重置密码是否启用 */
    private boolean emailResetPasswordEnabled = true;
}
