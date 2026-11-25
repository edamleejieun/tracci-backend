package com.tracci.common.util;

import com.tracci.config.JwtProperties;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtUtilTest {

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        JwtProperties props = new JwtProperties();
        props.setSecretKey("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");
        props.setAccessTokenExpire(120);
        props.setRefreshTokenExpire(3600);
        jwtUtil = new JwtUtil(props);
    }

    @Test
    void accessToken_roundTrip_subjectAndType() {
        Instant now = Instant.now();
        String userId = UUID.randomUUID().toString();
        String token = jwtUtil.createAccessToken(now, userId);
        assertEquals(userId, jwtUtil.parseAccessTokenUserId(token));
    }

    @Test
    void refreshToken_roundTrip_carryJti() {
        Instant now = Instant.now();
        String userId = UUID.randomUUID().toString();
        String jti = UUID.randomUUID().toString();
        String token = jwtUtil.createRefreshToken(now, userId, jti);
        JwtUtil.RefreshTokenPayload p = jwtUtil.parseRefreshToken(token);
        assertEquals(userId, p.userId());
        assertEquals(jti, p.jti());
    }

    @Test
    void accessToken_rejectsWhenParsedAsRefresh() {
        Instant now = Instant.now();
        String token = jwtUtil.createAccessToken(now, UUID.randomUUID().toString());
        assertThrows(JwtException.class, () -> jwtUtil.parseRefreshToken(token));
    }

    @Test
    void malformedToken_throws() {
        assertThrows(JwtException.class, () -> jwtUtil.parseAccessTokenUserId("not-a.jwt"));
    }
}
