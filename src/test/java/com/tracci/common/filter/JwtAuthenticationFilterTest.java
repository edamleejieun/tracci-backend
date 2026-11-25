package com.tracci.common.filter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tracci.common.context.LoginUserContext;
import com.tracci.common.response.ErrorCode;
import com.tracci.common.support.UserAccountStatus;
import com.tracci.common.util.JwtUtil;
import com.tracci.config.AuthWebProperties;
import com.tracci.config.JwtProperties;
import com.tracci.module.user.entity.User;
import com.tracci.module.user.repository.UserIncludeDeletedJdbc;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JwtAuthenticationFilterTest {

    @Mock
    private AuthWebProperties authWebProperties;
    @Mock
    private UserIncludeDeletedJdbc userIncludeDeletedJdbc;

    private JwtUtil jwtUtil;
    private JwtAuthenticationFilter filter;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        JwtProperties props = new JwtProperties();
        props.setSecretKey("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");
        props.setAccessTokenExpire(300);
        props.setRefreshTokenExpire(600);
        jwtUtil = new JwtUtil(props);
        objectMapper = new ObjectMapper();
        filter = new JwtAuthenticationFilter(jwtUtil, authWebProperties, objectMapper, userIncludeDeletedJdbc);
        when(authWebProperties.getPublicPathPatterns()).thenReturn(
                List.of("/api/v1/auth/login", "/actuator/health"));
    }

    @Test
    void publicPath_journeyShare_invokesChainWithoutAuthorizationHeader() throws Exception {
        when(authWebProperties.getPublicPathPatterns()).thenReturn(
                List.of("/api/v1/public/**", "/api/v1/auth/login"));

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setMethod("GET");
        req.setRequestURI("/api/v1/public/journeys/trip-1/share");

        MockHttpServletResponse res = new MockHttpServletResponse();
        AtomicBoolean next = new AtomicBoolean(false);
        FilterChain chain = (r, response) -> next.set(true);

        filter.doFilter(req, res, chain);

        assertTrue(next.get());
        assertEquals(200, res.getStatus());
    }

    @Test
    void publicPath_invokesChainWithoutAuthorizationHeader() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setMethod("POST");
        req.setRequestURI("/api/v1/auth/login");

        MockHttpServletResponse res = new MockHttpServletResponse();
        AtomicBoolean next = new AtomicBoolean(false);
        FilterChain chain = (r, response) -> next.set(true);

        filter.doFilter(req, res, chain);

        assertTrue(next.get());
        assertEquals(200, res.getStatus());
    }

    @Test
    void protectedPath_placesListMissingBearer_returns401() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setMethod("GET");
        req.setRequestURI("/api/v1/places");

        MockHttpServletResponse res = new MockHttpServletResponse();
        AtomicBoolean next = new AtomicBoolean(false);
        FilterChain chain = (r, response) -> next.set(true);

        filter.doFilter(req, res, chain);

        assertEquals(401, res.getStatus());
        assertFalse(next.get());
        JsonNode node = objectMapper.readTree(res.getContentAsString());
        assertEquals(ErrorCode.TOKEN_MISSING.getCode(), node.get("code").asInt());
    }

    @Test
    void protectedPath_missingBearer_returns401() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setMethod("GET");
        req.setRequestURI("/api/v1/users/me");

        MockHttpServletResponse res = new MockHttpServletResponse();
        AtomicBoolean next = new AtomicBoolean(false);
        FilterChain chain = (r, response) -> next.set(true);

        filter.doFilter(req, res, chain);

        assertEquals(401, res.getStatus());
        assertFalse(next.get());
        JsonNode node = objectMapper.readTree(res.getContentAsString());
        assertEquals(ErrorCode.TOKEN_MISSING.getCode(), node.get("code").asInt());
    }

    @Test
    void protectedPath_validAccessToken_invokesChain() throws Exception {
        String token = jwtUtil.createAccessToken(Instant.now(), "user-1");
        when(userIncludeDeletedJdbc.findById("user-1")).thenReturn(activeUser("user-1"));

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setMethod("GET");
        req.setRequestURI("/api/v1/users/me");
        req.addHeader("Authorization", "Bearer " + token);

        MockHttpServletResponse res = new MockHttpServletResponse();
        AtomicBoolean next = new AtomicBoolean(false);
        FilterChain chain = (r, response) -> {
            assertEquals("user-1", LoginUserContext.getUserIdOrNull());
            next.set(true);
        };

        filter.doFilter(req, res, chain);

        assertTrue(next.get());
        assertNull(LoginUserContext.getUserIdOrNull());
    }

    @Test
    void protectedPath_disabledUser_returns403() throws Exception {
        String token = jwtUtil.createAccessToken(Instant.now(), "user-1");
        User user = activeUser("user-1");
        user.setStatus("disabled");
        when(userIncludeDeletedJdbc.findById("user-1")).thenReturn(user);

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setMethod("GET");
        req.setRequestURI("/api/v1/users/me");
        req.addHeader("Authorization", "Bearer " + token);

        MockHttpServletResponse res = new MockHttpServletResponse();
        AtomicBoolean next = new AtomicBoolean(false);

        filter.doFilter(req, res, (r, response) -> next.set(true));

        assertEquals(403, res.getStatus());
        assertFalse(next.get());
        JsonNode node = objectMapper.readTree(res.getContentAsString());
        assertEquals(ErrorCode.FORBIDDEN.getCode(), node.get("code").asInt());
        assertNull(LoginUserContext.getUserIdOrNull());
    }

    @Test
    void protectedPath_deletedUser_returns403AccountDeleted() throws Exception {
        String token = jwtUtil.createAccessToken(Instant.now(), "user-1");
        User user = activeUser("user-1");
        user.setStatus(UserAccountStatus.DELETED);
        when(userIncludeDeletedJdbc.findById("user-1")).thenReturn(user);

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setMethod("GET");
        req.setRequestURI("/api/v1/users/me");
        req.addHeader("Authorization", "Bearer " + token);

        MockHttpServletResponse res = new MockHttpServletResponse();
        AtomicBoolean next = new AtomicBoolean(false);

        filter.doFilter(req, res, (r, response) -> next.set(true));

        assertEquals(403, res.getStatus());
        assertFalse(next.get());
        JsonNode node = objectMapper.readTree(res.getContentAsString());
        assertEquals(ErrorCode.ACCOUNT_DELETED.getCode(), node.get("code").asInt());
    }

    @Test
    void optionsRequest_skipsFilter() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setMethod("OPTIONS");
        req.setRequestURI("/api/v1/users/me");

        MockHttpServletResponse res = new MockHttpServletResponse();
        AtomicBoolean next = new AtomicBoolean(false);
        filter.doFilter(req, res, (r, response) -> next.set(true));

        assertTrue(next.get());
    }

    private static User activeUser(String id) {
        User user = new User();
        user.setId(id);
        user.setStatus("active");
        return user;
    }
}
