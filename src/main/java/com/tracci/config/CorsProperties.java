package com.tracci.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 浏览器跨域（本地联调 / 前后端分离部署）
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "tracci.cors")
public class CorsProperties {

    /**
     * 允许的请求来源（Spring {@code allowedOriginPatterns}，可写 {@code http://localhost:*}）
     */
    private List<String> allowedOriginPatterns = new ArrayList<>();
}
