package com.tracci.module.auth.service;

import com.tracci.common.exception.BizException;
import com.tracci.common.response.ErrorCode;
import com.tracci.common.support.UserAccountStatus;
import com.tracci.module.auth.entity.UserAuthIdentity;
import com.tracci.module.auth.enums.AuthProvider;
import com.tracci.module.auth.enums.LoginType;
import com.tracci.module.auth.repository.UserAuthIdentityIncludeDeletedJdbc;
import com.tracci.module.auth.repository.UserAuthIdentityMapper;
import com.tracci.module.user.repository.UserIncludeDeletedJdbc;
import com.tracci.module.user.entity.User;
import com.tracci.module.user.repository.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DuplicateKeyException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthIdentityServiceImplTest {

    @Mock
    private UserAuthIdentityMapper identityMapper;
    @Mock
    private UserAuthIdentityIncludeDeletedJdbc identityIncludeDeletedJdbc;
    @Mock
    private UserMapper userMapper;
    @Mock
    private UserIncludeDeletedJdbc userIncludeDeletedJdbc;

    private AuthIdentityServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AuthIdentityServiceImpl(
                identityMapper, identityIncludeDeletedJdbc, userMapper, userIncludeDeletedJdbc);
    }

    // ====================================================================
    // 微信登录 identity 查找/创建
    // ====================================================================

    @Test
    void findOrCreateUserByIdentity_wechatMini_createsUserAndIdentity() {
        when(identityMapper.selectOne(any())).thenReturn(null);
        when(userMapper.insert(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId("new-user-id");
            return 1;
        });

        AuthIdentityLoginResult result = service.findOrCreateUserByIdentity(
                AuthProvider.WECHAT_MINI,
                "openid-1",
                null,
                LoginType.WECHAT_MINI);

        assertTrue(result.isNewUser());
        assertEquals("new-user-id", result.user().getId());
        assertEquals(LoginType.WECHAT_MINI.name(), result.user().getLoginType());
        assertEquals("旅人", result.user().getNickname());
        assertNotNull(result.user().getLastLoginAt());

        ArgumentCaptor<UserAuthIdentity> identityCaptor = ArgumentCaptor.forClass(UserAuthIdentity.class);
        verify(identityMapper).insert(identityCaptor.capture());
        assertEquals("new-user-id", identityCaptor.getValue().getUserId());
        assertEquals(AuthProvider.WECHAT_MINI.name(), identityCaptor.getValue().getProvider());
        assertEquals("openid-1", identityCaptor.getValue().getProviderUserId());
    }

    @Test
    void findOrCreateUserByIdentity_reusesUserWhenUnionIdExistsAcrossWechatProviders() {
        // 已有 WECHAT_APP identity 带 union-1
        UserAuthIdentity appIdentity = new UserAuthIdentity();
        appIdentity.setId("identity-app");
        appIdentity.setUserId("user-1");
        appIdentity.setProvider(AuthProvider.WECHAT_APP.name());
        appIdentity.setProviderUserId("app-openid");
        appIdentity.setUnionId("union-1");

        User user = new User();
        user.setId("user-1");
        user.setStatus("active");
        user.setLoginType(LoginType.WECHAT_IOS.name());

        // 第一次 selectOne（按 WECHAT_MINI + openid 查找）返回 null
        // 第二次 selectOne（按 unionId 查找）返回已有的 app identity
        when(identityMapper.selectOne(any())).thenReturn(null, appIdentity);
        when(userIncludeDeletedJdbc.findById("user-1")).thenReturn(user);

        AuthIdentityLoginResult result = service.findOrCreateUserByIdentity(
                AuthProvider.WECHAT_MINI,
                "mini-openid",
                "union-1",
                LoginType.WECHAT_MINI);

        assertFalse(result.isNewUser());
        assertEquals("user-1", result.user().getId());
        verify(userMapper).update(any(), any());

        // 验证创建了新的 WECHAT_MINI identity 附加到已有用户
        ArgumentCaptor<UserAuthIdentity> identityCaptor = ArgumentCaptor.forClass(UserAuthIdentity.class);
        verify(identityMapper).insert(identityCaptor.capture());
        assertEquals("user-1", identityCaptor.getValue().getUserId());
        assertEquals(AuthProvider.WECHAT_MINI.name(), identityCaptor.getValue().getProvider());
        assertEquals("mini-openid", identityCaptor.getValue().getProviderUserId());
        assertEquals("union-1", identityCaptor.getValue().getUnionId());
    }

    @Test
    void findOrCreateUserByIdentity_createsNewUserWhenBindingPointsToDeletedAccount() {
        UserAuthIdentity oldIdentity = new UserAuthIdentity();
        oldIdentity.setId("identity-old");
        oldIdentity.setUserId("deleted-user");
        oldIdentity.setProvider(AuthProvider.WECHAT_MINI.name());
        oldIdentity.setProviderUserId("mini-openid");

        User deletedUser = new User();
        deletedUser.setId("deleted-user");
        deletedUser.setStatus(UserAccountStatus.DELETED);

        when(identityMapper.selectOne(any())).thenReturn(oldIdentity);
        when(userIncludeDeletedJdbc.findById("deleted-user")).thenReturn(deletedUser);
        when(userMapper.insert(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId("new-user-id");
            return 1;
        });

        AuthIdentityLoginResult result = service.findOrCreateUserByIdentity(
                AuthProvider.WECHAT_MINI,
                "mini-openid",
                null,
                LoginType.WECHAT_MINI);

        assertTrue(result.isNewUser());
        assertEquals("new-user-id", result.user().getId());
        verify(identityMapper).update(any(), any());
        verify(identityMapper).insert(any(UserAuthIdentity.class));
    }

    @Test
    void findOrCreateUserByIdentity_usesWechatNicknameForNewUser() {
        when(identityMapper.selectOne(any())).thenReturn(null);
        when(userMapper.insert(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId("new-user-id");
            return 1;
        });

        AuthIdentityLoginResult result = service.findOrCreateUserByIdentity(
                AuthProvider.WECHAT_APP,
                "ios-openid-1",
                "union-1",
                LoginType.WECHAT_IOS,
                "  小明  ");

        assertTrue(result.isNewUser());
        assertEquals("小明", result.user().getNickname());
    }

    @Test
    void findOrCreateUserByIdentity_rejectsDuplicateBindingOwnedByAnotherUser() {
        UserAuthIdentity existingIdentity = new UserAuthIdentity();
        existingIdentity.setId("identity-existing");
        existingIdentity.setUserId("existing-user");
        existingIdentity.setProvider(AuthProvider.WECHAT_MINI.name());
        existingIdentity.setProviderUserId("openid-1");

        when(identityMapper.selectOne(any())).thenReturn(null, existingIdentity);
        when(userMapper.insert(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId("new-user-id");
            return 1;
        });
        when(identityMapper.insert(any(UserAuthIdentity.class))).thenThrow(new DuplicateKeyException("duplicate"));

        BizException ex = assertThrows(BizException.class, () -> service.findOrCreateUserByIdentity(
                AuthProvider.WECHAT_MINI,
                "openid-1",
                null,
                LoginType.WECHAT_MINI));

        assertEquals(ErrorCode.WECHAT_LOGIN_FAILED, ex.getErrorCode());
    }

    // ====================================================================
    // EMAIL identity
    // ====================================================================

    @Test
    void createEmailIdentity_createsEmailProviderIdentity() {
        service.createEmailIdentity("user-1", "User@Example.COM");

        ArgumentCaptor<UserAuthIdentity> captor = ArgumentCaptor.forClass(UserAuthIdentity.class);
        verify(identityMapper).insert(captor.capture());
        assertEquals("user-1", captor.getValue().getUserId());
        assertEquals(AuthProvider.EMAIL.name(), captor.getValue().getProvider());
        assertEquals("user@example.com", captor.getValue().getProviderUserId());
        assertEquals("user@example.com", captor.getValue().getEmail());
        assertEquals("active", captor.getValue().getStatus());
    }
}
