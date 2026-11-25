package com.tracci.common.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记接口（或 Controller）默认处于「白名单」语义下的文档提示。
 * 实际放行由 {@code JwtAuthenticationFilter} 与 {@code tracci.auth.public-path-patterns} 控制。
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface NoAuth {
}
