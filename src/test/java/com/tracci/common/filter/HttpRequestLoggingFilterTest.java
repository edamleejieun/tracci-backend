package com.tracci.common.filter;

import com.tracci.config.RequestLoggingProperties;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(OutputCaptureExtension.class)
class HttpRequestLoggingFilterTest {

    private HttpRequestLoggingFilter filter;

    @BeforeEach
    void setUp() {
        RequestLoggingProperties properties = new RequestLoggingProperties();
        properties.setEnabled(true);
        properties.setSkipPathPatterns(List.of("/actuator/health", "/actuator/info"));
        filter = new HttpRequestLoggingFilter(properties);
    }

    @Test
    void apiRequest_logsRequestSummary(CapturedOutput output) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("GET");
        request.setRequestURI("/api/v1/trips");
        request.setRemoteAddr("10.0.0.2");
        request.addHeader("X-Forwarded-For", "203.0.113.10, 10.0.0.2");
        request.addHeader("X-Request-Id", "req-1");
        request.addHeader("User-Agent", "Tracci-iOS/1.0");
        request.setAttribute(HttpRequestLoggingFilter.REQUEST_USER_ID_ATTRIBUTE, "user-1");

        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> ((MockHttpServletResponse) res).setStatus(201);

        filter.doFilter(request, response, chain);

        String logs = output.getOut();
        assertTrue(logs.contains("HTTP request method=GET path=/api/v1/trips status=201"));
        assertTrue(logs.contains("userId=user-1"));
        assertTrue(logs.contains("ip=203.0.113.10"));
        assertTrue(logs.contains("requestId=req-1"));
        assertTrue(logs.contains("ua=Tracci-iOS/1.0"));
    }

    @Test
    void skippedPath_doesNotLog(CapturedOutput output) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("GET");
        request.setRequestURI("/actuator/health");

        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean next = new AtomicBoolean(false);

        filter.doFilter(request, response, (req, res) -> next.set(true));

        assertTrue(next.get());
        assertTrue(output.getOut().isBlank());
    }

    @Test
    void requestFailure_logsServerStatus(CapturedOutput output) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("POST");
        request.setRequestURI("/api/v1/trips");

        MockHttpServletResponse response = new MockHttpServletResponse();

        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> filter.doFilter(request, response, (req, res) -> {
                    throw new RuntimeException("boom");
                }));

        assertEquals("boom", thrown.getMessage());
        assertTrue(output.getOut().contains("HTTP request method=POST path=/api/v1/trips status=500"));
    }
}
