package com.tracci.service;

import com.tracci.config.AdminProperties;
import com.tracci.module.user.entity.User;
import com.tracci.module.user.repository.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private AdminProperties adminProperties;

    private AdminService adminService;

    @BeforeEach
    void setUp() {
        adminService = new AdminService(adminProperties, userMapper);
    }

    @Test
    void isCurrentUserAdmin_returnsFalse_whenNoUserId() {
        com.tracci.common.context.LoginUserContext.clear();

        assertThat(adminService.isCurrentUserAdmin()).isFalse();
    }

    @Test
    void isCurrentUserAdmin_matchesByUserId() {
        String adminUserId = "user-admin-001";
        when(adminProperties.getUserIds()).thenReturn(Set.of(adminUserId));
        com.tracci.common.context.LoginUserContext.setUserId(adminUserId);

        assertThat(adminService.isCurrentUserAdmin()).isTrue();
    }

    @Test
    void isCurrentUserAdmin_fallsBackToEmailCheck() {
        String uuidUserId = UUID.randomUUID().toString();
        User user = new User();
        user.setEmail("admin@tracci.local");

        when(adminProperties.getUserIds()).thenReturn(Set.of());
        when(adminProperties.getAdminEmails()).thenReturn(Set.of("admin@tracci.local"));
        when(userMapper.selectById(uuidUserId)).thenReturn(user);
        com.tracci.common.context.LoginUserContext.setUserId(uuidUserId);
        adminService.refreshAdminEmailCache();

        assertThat(adminService.isCurrentUserAdmin()).isTrue();
    }

    @Test
    void isCurrentUserAdmin_returnsFalse_forNonAdminEmail() {
        String uuidUserId = UUID.randomUUID().toString();
        User user = new User();
        user.setEmail("regular@tracci.local");

        when(adminProperties.getUserIds()).thenReturn(Set.of());
        when(adminProperties.getAdminEmails()).thenReturn(Set.of("admin@tracci.local"));
        when(userMapper.selectById(uuidUserId)).thenReturn(user);
        com.tracci.common.context.LoginUserContext.setUserId(uuidUserId);
        adminService.refreshAdminEmailCache();

        assertThat(adminService.isCurrentUserAdmin()).isFalse();
    }

    @Test
    void isCurrentUserAdmin_returnsFalse_whenUserNotFound() {
        String uuidUserId = UUID.randomUUID().toString();

        when(adminProperties.getUserIds()).thenReturn(Set.of());
        when(adminProperties.getAdminEmails()).thenReturn(Set.of("admin@tracci.local"));
        when(userMapper.selectById(uuidUserId)).thenReturn(null);
        com.tracci.common.context.LoginUserContext.setUserId(uuidUserId);
        adminService.refreshAdminEmailCache();

        assertThat(adminService.isCurrentUserAdmin()).isFalse();
    }
}
