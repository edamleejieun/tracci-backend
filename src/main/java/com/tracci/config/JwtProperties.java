package com.tracci.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT 签发配置，对应 application / 环境变量中的 tracci.jwt
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "tracci.jwt")
public class JwtProperties {

    /** HS256 密钥，生产环境必须使用 256bit+ 随机串并通过环境变量注入 */
    private String secretKey;

    /** Access Token 过期秒数 */
    private long accessTokenExpire = 7200L;

    /** Refresh Token 过期秒数 */
    private long refreshTokenExpire = 2592000L;
}
