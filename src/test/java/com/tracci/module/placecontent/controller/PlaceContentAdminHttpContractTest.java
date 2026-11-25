package com.tracci.module.placecontent.controller;

import com.tracci.common.context.LoginUserContext;
import com.tracci.common.exception.GlobalExceptionHandler;
import com.tracci.module.placecontent.service.PlaceContentService;
import com.tracci.service.AdminService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PlaceContentAdminHttpContractTest {

    @Mock
    private PlaceContentService placeContentService;

    @Mock
    private AdminService adminService;

    @AfterEach
    void tearDown() {
        LoginUserContext.clear();
    }

    @Test
    void publish_adminPassesIdempotencyAndRequestHeadersToService() throws Exception {
        LoginUserContext.setUserId("admin-001");
        when(adminService.isCurrentUserAdmin()).thenReturn(true);

        mockMvc().perform(post("/internal/v1/place-content-versions/version-001/publish")
                        .header("Idempotency-Key", "publish-key-001")
                        .header("X-Request-Id", "request-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(placeContentService).publishVersion(
                "version-001", "admin-001", "publish-key-001", "request-001");
    }

    @Test
    void publish_missingIdempotencyHeader_isRejectedBeforeServiceCall() throws Exception {
        LoginUserContext.setUserId("admin-001");

        mockMvc().perform(post("/internal/v1/place-content-versions/version-001/publish"))
                .andExpect(status().isBadRequest());

        verify(placeContentService, never()).publishVersion(
                "version-001", "admin-001", null, null);
    }

    @Test
    void publish_nonAdmin_isForbidden() throws Exception {
        LoginUserContext.setUserId("user-001");
        when(adminService.isCurrentUserAdmin()).thenReturn(false);

        mockMvc().perform(post("/internal/v1/place-content-versions/version-001/publish")
                        .header("Idempotency-Key", "publish-key-001"))
                .andExpect(status().isForbidden());

        verify(placeContentService, never()).publishVersion(
                "version-001", "user-001", "publish-key-001", null);
    }

    @Test
    void publish_withoutAuthenticatedUser_isUnauthorized() throws Exception {
        mockMvc().perform(post("/internal/v1/place-content-versions/version-001/publish")
                        .header("Idempotency-Key", "publish-key-001"))
                .andExpect(status().isUnauthorized());
    }

    private MockMvc mockMvc() {
        return MockMvcBuilders
                .standaloneSetup(new PlaceContentAdminController(
                        placeContentService, adminService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }
}
