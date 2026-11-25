package com.tracci.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 鉴权 Web 配置：无需携带 AccessToken 的路径（Ant 风格）
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "tracci.auth")
public class AuthWebProperties {

    private List<String> publicPathPatterns = new ArrayList<>();
}
