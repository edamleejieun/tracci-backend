package com.tracci.module.user.service;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.tracci.common.context.LoginUserContext;
import com.tracci.common.exception.BizException;
import com.tracci.common.response.ErrorCode;
import com.tracci.common.support.UserAccountStatus;
import com.tracci.integration.cos.FileUrlResolver;
import com.tracci.module.auth.repository.UserAuthIdentityMapper;
import com.tracci.module.auth.service.AuthIdentityService;
import com.tracci.module.auth.support.AuthRedisKeys;
import com.tracci.module.file.repository.FileMapper;
import com.tracci.module.trip.repository.TripMapper;
import com.tracci.module.user.dto.request.DeleteAccountReq;
import com.tracci.module.user.entity.User;
import com.tracci.module.user.repository.UserIncludeDeletedJdbc;
import com.tracci.module.user.repository.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserAccountDeletionTest {

    @Mock
    private UserMapper userMapper;
    @Mock
    private UserIncludeDeletedJdbc userIncludeDeletedJdbc;
    @Mock
    private FileMapper fileMapper;
    @Mock
    private FileUrlResolver fileUrlResolver;
    @Mock
    private AuthIdentityService authIdentityService;
    @Mock
    private UserAuthIdentityMapper identityMapper;
    @Mock
    private StringRedisTemplate redis;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private TripMapper tripMapper;

    private UserServiceImpl userService;

    @BeforeEach
    void setUp() {
        userService = new UserServiceImpl(
                userMapper, userIncludeDeletedJdbc, fileMapper, fileUrlResolver,
                authIdentityService, identityMapper, redis, passwordEncoder, tripMapper);
    }

    @Test
    void deleteAccount_success_anonymizesAndMarksDeleted() {
        User user = activeUserWithPassword();
        when(passwordEncoder.matches(any(), any())).thenReturn(true);
        when(userIncludeDeletedJdbc.findById("u-1")).thenReturn(user);
        when(userMapper.update(eq(null), any(UpdateWrapper.class))).thenReturn(1);

        LoginUserContext.setUserId("u-1");
        try {
            userService.deleteAccount(new DeleteAccountReq("DELETE", "不再使用", "secret"));
        } finally {
            LoginUserContext.clear();
        }

        verify(authIdentityService).releaseAllIdentitiesForDeletedUser("u-1");
        verify(redis).delete(AuthRedisKeys.refreshToken("u-1"));
        verify(userMapper).update(eq(null), any(UpdateWrapper.class));
    }

    @Test
    void deleteAccount_returnsWhenConcurrentRequestAlreadyDeleted() {
        User active = activeUserWithPassword();
        User deleted = new User();
        deleted.setId("u-1");
        deleted.setStatus(UserAccountStatus.DELETED);
        when(passwordEncoder.matches(any(), any())).thenReturn(true);
        when(userIncludeDeletedJdbc.findById("u-1")).thenReturn(active, deleted);
        when(userMapper.update(eq(null), any(UpdateWrapper.class))).thenReturn(0);

        LoginUserContext.setUserId("u-1");
        try {
            userService.deleteAccount(new DeleteAccountReq("DELETE", null, "secret"));
        } finally {
            LoginUserContext.clear();
        }

        verify(authIdentityService).releaseAllIdentitiesForDeletedUser("u-1");
        verify(redis).delete(AuthRedisKeys.refreshToken("u-1"));
        verify(userMapper).update(eq(null), any(UpdateWrapper.class));
    }

    @Test
    void deleteAccount_idempotentWhenAlreadyDeleted() {
        User user = new User();
        user.setId("u-1");
        user.setStatus(UserAccountStatus.DELETED);
        when(userIncludeDeletedJdbc.findById("u-1")).thenReturn(user);

        LoginUserContext.setUserId("u-1");
        try {
            userService.deleteAccount(new DeleteAccountReq("DELETE", null, null));
        } finally {
            LoginUserContext.clear();
        }

        verify(authIdentityService, never()).releaseAllIdentitiesForDeletedUser(any());
        verify(redis, never()).delete(any(String.class));
        verify(userMapper, never()).update(any(), any(UpdateWrapper.class));
    }

    @Test
    void deleteAccount_rejectsWrongConfirmText() {
        User user = activeUserWithPassword();
        when(userIncludeDeletedJdbc.findById("u-1")).thenReturn(user);

        LoginUserContext.setUserId("u-1");
        try {
            BizException ex = assertThrows(BizException.class,
                    () -> userService.deleteAccount(new DeleteAccountReq("WRONG", null, "secret")));
            assertEquals(ErrorCode.AUTH_PARAM_INVALID, ex.getErrorCode());
        } finally {
            LoginUserContext.clear();
        }
    }

    @Test
    void deleteAccount_requiresPasswordWhenPasswordSet() {
        User user = activeUserWithPassword();
        when(userIncludeDeletedJdbc.findById("u-1")).thenReturn(user);
        when(passwordEncoder.matches(any(), any())).thenReturn(false);

        LoginUserContext.setUserId("u-1");
        try {
            assertThrows(BizException.class,
                    () -> userService.deleteAccount(new DeleteAccountReq("DELETE", null, "bad")));
        } finally {
            LoginUserContext.clear();
        }
    }

    @Test
    void deleteAccount_rejectsOwnerOfSharedTripBeforeIdentityRelease() {
        User user = activeUserWithPassword();
        when(userIncludeDeletedJdbc.findById("u-1")).thenReturn(user);
        when(passwordEncoder.matches(any(), any())).thenReturn(true);
        when(tripMapper.countOwnedTripsWithOtherEffectiveMembers("u-1")).thenReturn(1L);

        LoginUserContext.setUserId("u-1");
        try {
            BizException ex = assertThrows(BizException.class,
                    () -> userService.deleteAccount(new DeleteAccountReq("DELETE", null, "secret")));
            assertEquals(ErrorCode.ACCOUNT_OWNS_SHARED_TRIPS, ex.getErrorCode());
        } finally {
            LoginUserContext.clear();
        }

        verify(authIdentityService, never()).releaseAllIdentitiesForDeletedUser(any());
        verify(userMapper, never()).update(any(), any(UpdateWrapper.class));
    }

    @Test
    void getMe_rejectsDeletedUser() {
        User user = new User();
        user.setId("u-1");
        user.setStatus(UserAccountStatus.DELETED);
        when(userIncludeDeletedJdbc.findById("u-1")).thenReturn(user);

        LoginUserContext.setUserId("u-1");
        try {
            BizException ex = assertThrows(BizException.class, () -> userService.getMe());
            assertEquals(ErrorCode.ACCOUNT_DELETED, ex.getErrorCode());
        } finally {
            LoginUserContext.clear();
        }
    }

    private static User activeUserWithPassword() {
        User user = new User();
        user.setId("u-1");
        user.setStatus(UserAccountStatus.ACTIVE);
        user.setEmail("traveler@example.com");
        user.setPasswordHash("$2a$hash");
        user.setPhone("+8613800138000");
        user.setNickname("旅行者");
        return user;
    }
}
