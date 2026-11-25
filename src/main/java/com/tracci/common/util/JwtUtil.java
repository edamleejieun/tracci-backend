package com.tracci.common.util;

import com.tracci.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

/**
 * JWT 工具：签发 / 解析 AccessToken、RefreshToken。
 * <p>
 * AccessToken claim {@code typ} = access；RefreshToken {@code typ} = refresh，且带 {@code jti}
 */
@Component
public class JwtUtil {

    public static final String CLAIM_TYP = "typ";
    public static final String TYP_ACCESS = "access";
    public static final String TYP_REFRESH = "refresh";
    public static final String CLAIM_JTI = "jti";

    private final JwtProperties jwtProperties;
    private final SecretKey secretKey;

    public JwtUtil(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
        this.secretKey = Keys.hmacShaKeyFor(
                jwtProperties.getSecretKey().getBytes(StandardCharsets.UTF_8));
    }

    public String createAccessToken(Instant now, String userId) {
        Instant exp = now.plusSeconds(jwtProperties.getAccessTokenExpire());
        return Jwts.builder()
                .subject(userId)
                .claim(CLAIM_TYP, TYP_ACCESS)
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .signWith(secretKey)
                .compact();
    }

    public String createRefreshToken(Instant now, String userId, String jti) {
        Instant exp = now.plusSeconds(jwtProperties.getRefreshTokenExpire());
        return Jwts.builder()
                .subject(userId)
                .id(jti)
                .claim(CLAIM_TYP, TYP_REFRESH)
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .signWith(secretKey)
                .compact();
    }

    public Claims parseAndVerify(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String parseAccessTokenUserId(String token) {
        Claims claims = parseAndVerify(token);
        if (!TYP_ACCESS.equals(claims.get(CLAIM_TYP, String.class))) {
            throw new JwtException("not an access token");
        }
        return requireSubject(claims);
    }

    public RefreshTokenPayload parseRefreshToken(String token) {
        Claims claims = parseAndVerify(token);
        if (!TYP_REFRESH.equals(claims.get(CLAIM_TYP, String.class))) {
            throw new JwtException("not a refresh token");
        }
        String jti = claims.getId();
        if (jti == null || jti.isBlank()) {
            throw new JwtException("missing jti");
        }
        return new RefreshTokenPayload(requireSubject(claims), jti);
    }

    public long getAccessTokenExpireSeconds() {
        return jwtProperties.getAccessTokenExpire();
    }

    public long getRefreshTokenExpireSeconds() {
        return jwtProperties.getRefreshTokenExpire();
    }

    private String requireSubject(Claims claims) {
        String subject = claims.getSubject();
        if (subject == null || subject.isBlank()) {
            throw new JwtException("missing subject");
        }
        return subject;
    }

    public record RefreshTokenPayload(String userId, String jti) {
    }
}
