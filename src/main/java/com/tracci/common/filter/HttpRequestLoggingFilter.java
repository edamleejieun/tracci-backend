package com.tracci.common.filter;

import com.tracci.common.context.LoginUserContext;
import com.tracci.config.RequestLoggingProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * 记录接口请求摘要，便于线上判断客户端请求是否到达后端。
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 40)
@RequiredArgsConstructor
public class HttpRequestLoggingFilter extends OncePerRequestFilter {

    public static final String REQUEST_USER_ID_ATTRIBUTE = "tracci.request.userId";

    private final RequestLoggingProperties properties;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!properties.isEnabled()) {
            return true;
        }
        String path = stripContextPath(request);
        List<String> patterns = properties.getSkipPathPatterns();
        if (patterns == null || patterns.isEmpty()) {
            return false;
        }
        for (String pattern : patterns) {
            if (pathMatcher.match(pattern, path)) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        long startNanos = System.nanoTime();
        Throwable failure = null;
        try {
            filterChain.doFilter(request, response);
        } catch (ServletException | IOException | RuntimeException ex) {
            failure = ex;
            throw ex;
        } finally {
            logRequest(request, response, startNanos, failure);
        }
    }

    private void logRequest(
            HttpServletRequest request,
            HttpServletResponse response,
            long startNanos,
            Throwable failure) {
        long durationMs = (System.nanoTime() - startNanos) / 1_000_000;
        int status = failure == null ? response.getStatus() : Math.max(response.getStatus(), 500);
        String userId = resolveUserId(request);
        String requestId = valueOrDash(request.getHeader("X-Request-Id"));
        String userAgent = abbreviate(valueOrDash(request.getHeader("User-Agent")), 160);

        log.info("HTTP request method={} path={} status={} durationMs={} userId={} ip={} requestId={} ua={}",
                request.getMethod(),
                stripContextPath(request),
                status,
                durationMs,
                userId,
                resolveClientIp(request),
                requestId,
                userAgent);
    }

    private static String resolveUserId(HttpServletRequest request) {
        Object userId = request.getAttribute(REQUEST_USER_ID_ATTRIBUTE);
        if (userId instanceof String value && StringUtils.hasText(value)) {
            return value;
        }
        String contextUserId = LoginUserContext.getUserIdOrNull();
        return StringUtils.hasText(contextUserId) ? contextUserId : "-";
    }

    private static String resolveClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwardedFor)) {
            int commaIndex = forwardedFor.indexOf(',');
            return commaIndex >= 0 ? forwardedFor.substring(0, commaIndex).trim() : forwardedFor.trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (StringUtils.hasText(realIp)) {
            return realIp.trim();
        }
        return valueOrDash(request.getRemoteAddr());
    }

    private static String stripContextPath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String context = request.getContextPath();
        if (context != null && !context.isEmpty() && uri.startsWith(context)) {
            return uri.substring(context.length());
        }
        return uri;
    }

    private static String valueOrDash(String value) {
        return StringUtils.hasText(value) ? value.trim() : "-";
    }

    private static String abbreviate(String value, int maxLength) {
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
