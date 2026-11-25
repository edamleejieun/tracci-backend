package com.tracci.common.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tracci.common.context.LoginUserContext;
import com.tracci.common.response.ApiResponse;
import com.tracci.common.response.ErrorCode;
import com.tracci.common.support.UserAccountStatus;
import com.tracci.common.util.JwtUtil;
import com.tracci.config.AuthWebProperties;
import com.tracci.module.user.entity.User;
import com.tracci.module.user.repository.UserIncludeDeletedJdbc;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * JWT 无状态鉴权过滤器（先于 Controller 执行）。
 * <p>
 * 白名单路径见 {@code tracci.auth.public-path-patterns}；其余请求必须携带
 * {@code Authorization: Bearer <access_token>}，且 Token {@code typ} 必须为 access。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 50)
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final AuthWebProperties authWebProperties;
    private final ObjectMapper objectMapper;
    private final UserIncludeDeletedJdbc userIncludeDeletedJdbc;

    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String path = stripContextPath(request);
        List<String> patterns = authWebProperties.getPublicPathPatterns();
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
        try {
            String header = request.getHeader("Authorization");
            if (header == null || !header.regionMatches(true, 0, "Bearer ", 0, "Bearer ".length())) {
                writeUnauthorized(response, ErrorCode.TOKEN_MISSING);
                return;
            }
            String token = header.substring("Bearer ".length()).trim();
            if (token.isEmpty()) {
                writeUnauthorized(response, ErrorCode.TOKEN_MISSING);
                return;
            }
            String userId = jwtUtil.parseAccessTokenUserId(token);
            User user = userIncludeDeletedJdbc.findById(userId);
            if (user == null) {
                writeUnauthorized(response, ErrorCode.TOKEN_INVALID);
                return;
            }
            if (!UserAccountStatus.isActive(user)) {
                if (UserAccountStatus.isDeleted(user)) {
                    writeForbidden(response, ErrorCode.ACCOUNT_DELETED);
                } else {
                    writeForbidden(response, ErrorCode.FORBIDDEN, "账号已禁用");
                }
                return;
            }
            LoginUserContext.setUserId(userId);
            request.setAttribute(HttpRequestLoggingFilter.REQUEST_USER_ID_ATTRIBUTE, userId);
            filterChain.doFilter(request, response);
        } catch (JwtException ex) {
            writeUnauthorized(response, ErrorCode.TOKEN_INVALID);
        } finally {
            LoginUserContext.clear();
        }
    }

    private static String stripContextPath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String context = request.getContextPath();
        if (context != null && !context.isEmpty() && uri.startsWith(context)) {
            return uri.substring(context.length());
        }
        return uri;
    }

    private void writeUnauthorized(HttpServletResponse response, ErrorCode errorCode) throws IOException {
        response.setStatus(errorCode.getHttpStatus());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), ApiResponse.fail(errorCode));
    }

    private void writeForbidden(HttpServletResponse response, ErrorCode errorCode) throws IOException {
        writeForbidden(response, errorCode, errorCode.getMessage());
    }

    private void writeForbidden(HttpServletResponse response, ErrorCode errorCode, String message) throws IOException {
        response.setStatus(errorCode.getHttpStatus());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), ApiResponse.fail(errorCode, message));
    }
}
