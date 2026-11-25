package com.tracci.config;

import com.tracci.module.auth.service.AuthIdentityService;
import com.tracci.module.user.entity.User;
import com.tracci.module.user.repository.UserMapper;
import com.tracci.service.AdminService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminInitializerTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private AuthIdentityService authIdentityService;

    @Mock
    private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    @Mock
    private AdminService adminService;

    private AdminInitializer initializer;

    @BeforeEach
    void setUp() {
        initializer = new AdminInitializer(
                createProperties("admin@test.local", "secret123"),
                userMapper, passwordEncoder, authIdentityService, adminService);
        org.mockito.Mockito.doNothing().when(adminService).refreshAdminEmailCache();
    }

    @Test
    void run_createsAdminUser_whenNotExists() {
        when(userMapper.selectOne(any(com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper.class)))
                .thenReturn(null);
        when(passwordEncoder.encode("secret123")).thenReturn("hashed");
        // Simulate MyBatis-Plus setting the ID after insert
        when(userMapper.insert(any(User.class))).thenAnswer(invocation -> {
            User u = invocation.getArgument(0);
            if (u.getId() == null) {
                u.setId("user-generated-" + java.util.UUID.randomUUID().toString().substring(0, 8));
            }
            return 1;
        });

        initializer.run();

        verify(userMapper).insert(argThat((User u) ->
                "admin@test.local".equals(u.getEmail())
                        && "hashed".equals(u.getPasswordHash())
                        && "active".equals(u.getStatus())
                        && "EMAIL".equals(u.getLoginType())));
        verify(authIdentityService).createEmailIdentity(anyString(), eq("admin@test.local"));
    }

    @Test
    void run_skipsWhenUserExists() {
        User existing = new User();
        existing.setId("user-existing");
        existing.setEmail("admin@test.local");

        when(userMapper.selectOne(any(com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper.class)))
                .thenReturn(existing);

        initializer.run();

        verify(userMapper, never()).insert((User) any());
        verify(authIdentityService, never()).createEmailIdentity(anyString(), anyString());
    }

    @Test
    void run_generatesRandomPassword_whenDefaultPasswordEmpty() {
        AdminProperties props = createProperties("admin@test.local", "");
        initializer = new AdminInitializer(props, userMapper, passwordEncoder, authIdentityService, adminService);

        when(userMapper.selectOne(any(com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper.class)))
                .thenReturn(null);
        when(passwordEncoder.encode(anyString())).thenAnswer(i -> "hashed:" + i.getArgument(0));

        initializer.run();

        verify(userMapper).insert(argThat((User u) -> u.getPasswordHash() != null && !u.getPasswordHash().isBlank()));
    }

    @Test
    void run_doesNothing_whenAdminEmailsEmpty() {
        AdminProperties props = new AdminProperties(); // empty lists
        initializer = new AdminInitializer(props, userMapper, passwordEncoder, authIdentityService, adminService);

        initializer.run();

        verify(userMapper, never()).selectOne(any());
        verify(userMapper, never()).insert((User) any());
    }

    private static AdminProperties createProperties(String email, String password) {
        AdminProperties props = new AdminProperties();
        props.setAdminEmails(Set.of(email));
        props.setDefaultPassword(password);
        return props;
    }
}
