package com.tracci.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * HTTP 请求摘要日志配置。
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "tracci.request-logging")
public class RequestLoggingProperties {

    /**
     * 是否启用统一接口请求日志。
     */
    private boolean enabled = true;

    /**
     * 不记录日志的路径（Ant 风格），用于健康检查等高频低价值请求。
     */
    private List<String> skipPathPatterns = new ArrayList<>();
}
