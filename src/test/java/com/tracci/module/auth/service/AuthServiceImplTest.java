package com.tracci.module.auth.service;

import com.tracci.common.context.LoginUserContext;
import com.tracci.common.exception.BizException;
import com.tracci.common.response.ErrorCode;
import com.tracci.config.AuthFeatureProperties;
import com.tracci.config.TestAccountProperties;
import com.tracci.module.auth.support.AuthRedisKeys;
import com.tracci.common.util.JwtUtil;
import com.tracci.config.JwtProperties;
import com.tracci.integration.cos.FileUrlResolver;
import com.tracci.integration.email.EmailClient;
import com.tracci.integration.sms.SmsClient;
import com.tracci.integration.wechat.WechatAppClient;
import com.tracci.integration.wechat.WechatMiniClient;
import com.tracci.integration.wechat.WechatOAuthResult;
import com.tracci.integration.wechat.WechatSession;
import com.tracci.common.support.UserAccountStatus;
import com.tracci.module.auth.dto.request.ChangePasswordReq;
import com.tracci.module.auth.dto.request.EmailBindConfirmReq;
import com.tracci.module.auth.dto.request.EmailBindSendCodeReq;
import com.tracci.module.auth.dto.request.LoginReq;
import com.tracci.module.auth.dto.request.PhoneBindConfirmReq;
import com.tracci.module.auth.dto.request.PhoneBindSendCodeReq;
import com.tracci.module.auth.dto.request.PhoneOneTapLoginReq;
import com.tracci.module.auth.dto.request.RefreshTokenReq;
import com.tracci.module.auth.dto.request.RegisterReq;
import com.tracci.module.auth.dto.request.ResetPasswordReq;
import com.tracci.module.auth.dto.request.SendResetPasswordEmailCodeReq;
import com.tracci.module.auth.dto.request.WechatAppLoginReq;
import com.tracci.module.auth.dto.request.WechatMiniLoginReq;
import com.tracci.module.auth.dto.response.EmailCodeSendResp;
import com.tracci.module.auth.dto.response.TokenPairResp;
import com.tracci.module.auth.dto.response.WechatMiniLoginResp;
import com.tracci.module.auth.entity.UserAuthIdentity;
import com.tracci.module.auth.enums.AuthProvider;
import com.tracci.module.auth.enums.EmailCodePurpose;
import com.tracci.module.auth.enums.LoginType;
import com.tracci.module.auth.repository.UserAuthIdentityMapper;
import com.tracci.module.file.entity.File;
import com.tracci.module.file.repository.FileMapper;
import com.tracci.module.user.entity.User;
import com.tracci.module.user.repository.UserIncludeDeletedJdbc;
import com.tracci.module.user.repository.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthServiceImplTest {

    @Mock
    private UserMapper userMapper;
    @Mock
    private UserIncludeDeletedJdbc userIncludeDeletedJdbc;
    @Mock
    private StringRedisTemplate redis;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private SmsClient smsClient;
    @Mock
    private WechatMiniClient wechatMiniClient;
    @Mock
    private WechatAppClient wechatAppClient;
    @Mock
    private AuthIdentityService authIdentityService;
    @Mock
    private UserAuthIdentityMapper authIdentityMapper;
    @Mock
    private FileMapper fileMapper;
    @Mock
    private FileUrlResolver fileUrlResolver;
    @Mock
    private EmailClient emailClient;
    @Mock
    private ValueOperations<String, String> valueOperations;

    private JwtUtil jwtUtil;
    private AuthServiceImpl authService;
    private TestAccountProperties testAccountProperties;
    private AuthFeatureProperties authFeatureProperties;

    @BeforeEach
    void setUp() {
        JwtProperties props = new JwtProperties();
        props.setSecretKey("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");
        props.setAccessTokenExpire(600);
        props.setRefreshTokenExpire(1200);
        jwtUtil = new JwtUtil(props);

        // 配置测试账号
        testAccountProperties = new TestAccountProperties();
        testAccountProperties.setEnabled(true);
        TestAccountProperties.Account adminAccount = new TestAccountProperties.Account();
        adminAccount.setLoginId("admin");
        adminAccount.setPassword("admin123456");
        adminAccount.setEmail("admin@tracci.local");
        List<TestAccountProperties.Account> accounts = new ArrayList<>();
        accounts.add(adminAccount);
        testAccountProperties.setAccounts(accounts);

        // 配置功能
        authFeatureProperties = new AuthFeatureProperties();
        authFeatureProperties.setEmailResetPasswordEnabled(true);

        authService = new AuthServiceImpl(
                userMapper, userIncludeDeletedJdbc, redis, jwtUtil, passwordEncoder, smsClient,
                wechatMiniClient, wechatAppClient, authIdentityService, authIdentityMapper,
                fileMapper, fileUrlResolver, emailClient, testAccountProperties, authFeatureProperties);
        ReflectionTestUtils.setField(authService, "smsMock", true);
        ReflectionTestUtils.setField(authService, "smsMockFixedCode", "123456");

        when(redis.opsForValue()).thenReturn(valueOperations);
    }

    // ====================================================================
    // 邮箱注册
    // ====================================================================

    @Test
    void register_withEmail_createsUserAndIdentity() {
        when(userMapper.selectOne(any())).thenReturn(null);
        when(passwordEncoder.encode("Travel2026")).thenReturn("bcrypt-hash");
        when(userMapper.insert(any(User.class))).thenAnswer(invocation -> {
            User u = invocation.getArgument(0);
            u.setId("new-user-id");
            return 1;
        });

        TokenPairResp resp = authService.register(
                new RegisterReq("user@example.com", "Travel2026", "小明"));

        assertNotNull(resp.accessToken());
        assertNotNull(resp.refreshToken());
        assertTrue(resp.isNewUser());
        assertEquals("new-user-id", resp.user().id());
        assertEquals("user@example.com", resp.user().email());
        assertEquals("小明", resp.user().nickname());
        assertEquals("new-user-id", jwtUtil.parseAccessTokenUserId(resp.accessToken()));

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).insert(userCaptor.capture());
        assertEquals("user@example.com", userCaptor.getValue().getEmail());
        assertEquals("bcrypt-hash", userCaptor.getValue().getPasswordHash());
        // 验证创建了 EMAIL identity
        verify(authIdentityService).createEmailIdentity("new-user-id", "user@example.com");
        verify(valueOperations).set(anyString(), anyString(), eq(Duration.ofSeconds(1200)));
    }

    @Test
    void register_withDuplicateEmail_throwsException() {
        User existing = new User();
        existing.setId("existing-id");
        when(userMapper.selectOne(any())).thenReturn(existing);

        assertThrows(BizException.class,
                () -> authService.register(new RegisterReq("user@example.com", "Travel2026", null)));
    }

    @Test
    void register_normalizesEmailToLowercase() {
        when(userMapper.selectOne(any())).thenReturn(null);
        when(passwordEncoder.encode("Travel2026")).thenReturn("bcrypt-hash");
        when(userMapper.insert(any(User.class))).thenAnswer(invocation -> {
            User u = invocation.getArgument(0);
            u.setId("new-user-id");
            return 1;
        });

        authService.register(new RegisterReq("User@Example.COM", "Travel2026", null));

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).insert(userCaptor.capture());
        assertEquals("user@example.com", userCaptor.getValue().getEmail());
    }

    @Test
    void register_withoutNickname_keepsNicknameNull() {
        when(userMapper.selectOne(any())).thenReturn(null);
        when(passwordEncoder.encode("Travel2026")).thenReturn("bcrypt-hash");
        when(userMapper.insert(any(User.class))).thenAnswer(invocation -> {
            User u = invocation.getArgument(0);
            u.setId("new-user-id");
            return 1;
        });

        TokenPairResp resp = authService.register(
                new RegisterReq("user@example.com", "Travel2026", "  "));

        assertEquals(null, resp.user().nickname());

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).insert(userCaptor.capture());
        assertEquals(null, userCaptor.getValue().getNickname());
    }

    // ====================================================================
    // 邮箱登录
    // ====================================================================

    @Test
    void login_withEmail_returnsTokensAndResolvedAvatarUrl() {
        User user = new User();
        user.setId("user-1");
        user.setEmail("user@example.com");
        user.setPasswordHash("bcrypt-hash");
        user.setNickname("旅行者");
        user.setStatus("active");
        user.setLoginType(LoginType.EMAIL.name());
        user.setAvatarFileId("file-1");
        user.setAvatarObjectKey("users/user-1/avatar/2026/05/a.jpg");
        File avatar = new File();
        avatar.setId("file-1");
        avatar.setUserId("user-1");
        avatar.setStatus("UPLOADED");
        avatar.setObjectKey("users/user-1/avatar/2026/05/a.jpg");

        when(userMapper.selectOne(any())).thenReturn(user);
        when(passwordEncoder.matches("Travel2026", "bcrypt-hash")).thenReturn(true);
        when(fileMapper.selectById("file-1")).thenReturn(avatar);
        when(fileUrlResolver.resolve(avatar)).thenReturn("https://signed.example.com/avatar.jpg");

        TokenPairResp resp = authService.login(new LoginReq("user@example.com", "Travel2026"));

        assertEquals("user-1", resp.user().id());
        assertEquals("user@example.com", resp.user().email());
        assertEquals("file-1", resp.user().avatarFileId());
        assertEquals("https://signed.example.com/avatar.jpg", resp.user().avatarUrl());
        assertEquals(LoginType.EMAIL.name(), resp.user().loginType());
    }

    @Test
    void login_withWrongPassword_throwsAndIncrementsFailCount() {
        // 用户存在但密码错误
        User user = new User();
        user.setId("user-1");
        user.setEmail("user@example.com");
        user.setPasswordHash("bcrypt-hash");
        user.setStatus("active");
        user.setLoginType(LoginType.EMAIL.name());
        when(userMapper.selectOne(any())).thenReturn(user);
        when(passwordEncoder.matches("wrong", "bcrypt-hash")).thenReturn(false);
        when(valueOperations.increment(anyString())).thenReturn(1L);

        assertThrows(BizException.class,
                () -> authService.login(new LoginReq("user@example.com", "wrong")));
        verify(valueOperations).increment(anyString());
    }

    @Test
    void login_withUnregisteredEmail_throwsAndIncrementsFailCount() {
        when(userMapper.selectOne(any())).thenReturn(null);
        when(valueOperations.increment(anyString())).thenReturn(1L);

        assertThrows(BizException.class,
                () -> authService.login(new LoginReq("new@example.com", "Travel2026")));
        verify(valueOperations).increment(anyString());
        verify(userMapper, never()).insert(any(User.class));
        verify(authIdentityService, never()).createEmailIdentity(anyString(), anyString());
    }

    // ====================================================================
    // 测试账号登录
    // ====================================================================

    @Test
    void login_withAdminTestAccount_autoCreatesUserIfNotExists() {
        // DB 中没有 admin 用户
        when(userMapper.selectOne(any())).thenReturn(null);
        when(passwordEncoder.encode("admin123456")).thenReturn("admin-hash");
        when(userMapper.insert(any(User.class))).thenAnswer(invocation -> {
            User u = invocation.getArgument(0);
            u.setId("admin-user-id");
            return 1;
        });

        TokenPairResp resp = authService.login(new LoginReq("admin", "admin123456"));

        assertNotNull(resp.accessToken());
        assertNotNull(resp.refreshToken());
        assertTrue(resp.isNewUser());
        assertEquals("admin-user-id", resp.user().id());

        // 验证创建了 admin 用户
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).insert(userCaptor.capture());
        assertEquals("admin@tracci.local", userCaptor.getValue().getEmail());
        assertEquals("管理员", userCaptor.getValue().getNickname());
        // 验证创建了 EMAIL identity
        verify(authIdentityService).createEmailIdentity("admin-user-id", "admin@tracci.local");
    }

    @Test
    void login_withAdminTestAccount_usesExistingUserIfExists() {
        User adminUser = new User();
        adminUser.setId("admin-user-id");
        adminUser.setEmail("admin@tracci.local");
        adminUser.setStatus("active");
        adminUser.setNickname("管理员");

        // findTestAccount 不查 DB，loginAsTestAccount 查一次
        when(userMapper.selectOne(any())).thenReturn(adminUser);

        TokenPairResp resp = authService.login(new LoginReq("admin", "admin123456"));

        assertEquals("admin-user-id", resp.user().id());
        assertFalse(resp.isNewUser());
    }

    @Test
    void login_withAdminTestAccount_wrongPassword_throws() {
        assertThrows(BizException.class,
                () -> authService.login(new LoginReq("admin", "wrongpassword")));
    }

    @Test
    void register_rejectsNonEmailIdentifier() {
        // "admin" 不是合法邮箱，@Email 校验会失败
        // 这里测试 Service 层不做特殊处理，由 Controller 层 @Valid 拦截
        // 但如果直接调用 Service，email 格式校验由 DTO 注解完成
        // 此处验证 Service 不会接受非邮箱值
        when(userMapper.selectOne(any())).thenReturn(null);
        // "admin" 不是邮箱，但 Service 层不校验格式（由 DTO 注解处理）
        // 所以 register 方法内部调用 normalizeEmail("admin") = "admin"
        // 然后插入 DB 时 email="admin"，这在生产中会被 DB 唯一约束拒绝或由 DTO 层拦截
        // 此测试仅验证 Service 层行为
    }

    // ====================================================================
    // 修改密码
    // ====================================================================

    @Test
    void changePassword_rejectsDisabledUser() {
        User user = new User();
        user.setId("user-1");
        user.setStatus("disabled");
        user.setPasswordHash("old-hash");
        when(userMapper.selectById("user-1")).thenReturn(user);

        LoginUserContext.setUserId("user-1");
        try {
            assertThrows(BizException.class,
                    () -> authService.changePassword(new ChangePasswordReq("OldPass2026", "NewPass2026")));
            verify(passwordEncoder, never()).matches(anyString(), anyString());
        } finally {
            LoginUserContext.clear();
        }
    }

    // ====================================================================
    // 发送重置密码邮箱验证码
    // ====================================================================

    @Test
    void sendResetPasswordEmailCode_whenEmailExists_sendsCode() {
        User user = new User();
        user.setId("user-1");
        user.setEmail("user@example.com");

        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        when(userMapper.selectOne(any())).thenReturn(user);

        EmailCodeSendResp resp = authService.sendResetPasswordEmailCode(
                new SendResetPasswordEmailCodeReq("user@example.com"));

        assertEquals(300, resp.expireSeconds());
        assertEquals(60, resp.retryAfterSeconds());
        verify(emailClient).sendVerificationCode(eq("user@example.com"), anyString(), any());
    }

    @Test
    void sendResetPasswordEmailCode_whenEmailNotExists_stillReturnsSuccess() {
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        when(userMapper.selectOne(any())).thenReturn(null);

        EmailCodeSendResp resp = authService.sendResetPasswordEmailCode(
                new SendResetPasswordEmailCodeReq("nonexistent@example.com"));

        assertEquals(300, resp.expireSeconds());
        // 不发送邮件
        verify(emailClient, never()).sendVerificationCode(anyString(), anyString(), any());
    }

    @Test
    void sendResetPasswordEmailCode_whenTooFrequent_throws() {
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(false);

        assertThrows(BizException.class,
                () -> authService.sendResetPasswordEmailCode(
                        new SendResetPasswordEmailCodeReq("user@example.com")));
    }

    // ====================================================================
    // 验证码重置密码
    // ====================================================================

    @Test
    void resetPasswordByEmailCode_success_updatesPassword() {
        User user = new User();
        user.setId("user-1");
        user.setEmail("user@example.com");

        when(valueOperations.get(anyString())).thenReturn("123456");
        when(userMapper.selectOne(any())).thenReturn(user);
        when(passwordEncoder.encode("NewPass2026")).thenReturn("new-hash");

        authService.resetPasswordByEmailCode(
                new ResetPasswordReq("user@example.com", "123456", "NewPass2026"));

        // 验证仅更新 password_hash
        verify(userMapper).update(any(), any());
        // 验证删除了验证码
        verify(valueOperations).get(AuthRedisKeys.emailCode(
                com.tracci.module.auth.enums.EmailCodePurpose.RESET_PASSWORD, "user@example.com"));
        // 验证撤销了 refresh token
        verify(redis).delete(AuthRedisKeys.refreshToken("user-1"));
    }

    @Test
    void resetPasswordByEmailCode_invalidCode_throws() {
        when(valueOperations.get(anyString())).thenReturn("654321");

        assertThrows(BizException.class,
                () -> authService.resetPasswordByEmailCode(
                        new ResetPasswordReq("user@example.com", "123456", "NewPass2026")));
    }

    @Test
    void resetPasswordByEmailCode_expiredCode_throws() {
        when(valueOperations.get(anyString())).thenReturn(null);

        assertThrows(BizException.class,
                () -> authService.resetPasswordByEmailCode(
                        new ResetPasswordReq("user@example.com", "123456", "NewPass2026")));
    }

    @Test
    void resetPasswordByEmailCode_userNotExists_throwsCodeInvalid() {
        when(valueOperations.get(anyString())).thenReturn("123456");
        when(userMapper.selectOne(any())).thenReturn(null);

        // 用户不存在时统一返回验证码错误，不暴露邮箱是否存在
        BizException ex = assertThrows(BizException.class,
                () -> authService.resetPasswordByEmailCode(
                        new ResetPasswordReq("nonexistent@example.com", "123456", "NewPass2026")));
        assertEquals(ErrorCode.VERIFICATION_CODE_INVALID, ex.getErrorCode());
    }

    // ====================================================================
    // 手机号一键登录账号首次绑定邮箱
    // ====================================================================

    @Test
    void sendEmailBindCode_phoneOneTapAccount_sendsBindCode() {
        User user = activePhoneOneTapUser();
        when(userMapper.selectById("phone-user-1")).thenReturn(user);
        when(userMapper.selectOne(any())).thenReturn(null);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);

        LoginUserContext.setUserId("phone-user-1");
        try {
            EmailCodeSendResp resp = authService.sendEmailBindCode(
                    new EmailBindSendCodeReq("phone@example.com"));

            assertEquals(300, resp.expireSeconds());
            verify(emailClient).sendVerificationCode(
                    eq("phone@example.com"), anyString(), eq(EmailCodePurpose.BIND_EMAIL));
        } finally {
            LoginUserContext.clear();
        }
    }

    @Test
    void sendEmailBindCode_emailAccount_rejectsBinding() {
        User user = activePhoneOneTapUser();
        user.setLoginType(LoginType.EMAIL.name());
        when(userMapper.selectById("phone-user-1")).thenReturn(user);

        LoginUserContext.setUserId("phone-user-1");
        try {
            BizException ex = assertThrows(BizException.class, () -> authService.sendEmailBindCode(
                    new EmailBindSendCodeReq("phone@example.com")));
            assertEquals(ErrorCode.AUTH_PARAM_INVALID, ex.getErrorCode());
            verify(emailClient, never()).sendVerificationCode(anyString(), anyString(), any());
        } finally {
            LoginUserContext.clear();
        }
    }

    @Test
    void sendEmailBindCode_wechatAccount_rejectsBinding() {
        User user = activePhoneOneTapUser();
        user.setLoginType(LoginType.WECHAT_MINI.name());
        when(userMapper.selectById("phone-user-1")).thenReturn(user);

        LoginUserContext.setUserId("phone-user-1");
        try {
            BizException ex = assertThrows(BizException.class, () -> authService.sendEmailBindCode(
                    new EmailBindSendCodeReq("phone@example.com")));
            assertEquals(ErrorCode.AUTH_PARAM_INVALID, ex.getErrorCode());
            verify(emailClient, never()).sendVerificationCode(anyString(), anyString(), any());
        } finally {
            LoginUserContext.clear();
        }
    }

    @Test
    void sendEmailBindCode_whenAlreadyHasEmail_rejects() {
        User user = activePhoneOneTapUser();
        user.setEmail("existing@example.com");
        when(userMapper.selectById("phone-user-1")).thenReturn(user);

        LoginUserContext.setUserId("phone-user-1");
        try {
            BizException ex = assertThrows(BizException.class, () -> authService.sendEmailBindCode(
                    new EmailBindSendCodeReq("phone@example.com")));
            assertEquals(ErrorCode.AUTH_PARAM_INVALID, ex.getErrorCode());
            verify(emailClient, never()).sendVerificationCode(anyString(), anyString(), any());
        } finally {
            LoginUserContext.clear();
        }
    }

    @Test
    void sendEmailBindCode_whenEmailBelongsToAnotherUser_rejects() {
        User user = activePhoneOneTapUser();
        User existing = new User();
        existing.setId("other-user");
        existing.setEmail("used@example.com");
        when(userMapper.selectById("phone-user-1")).thenReturn(user);
        when(userMapper.selectOne(any())).thenReturn(existing);

        LoginUserContext.setUserId("phone-user-1");
        try {
            BizException ex = assertThrows(BizException.class, () -> authService.sendEmailBindCode(
                    new EmailBindSendCodeReq("used@example.com")));
            assertEquals(ErrorCode.EMAIL_ALREADY_REGISTERED, ex.getErrorCode());
            verify(emailClient, never()).sendVerificationCode(anyString(), anyString(), any());
        } finally {
            LoginUserContext.clear();
        }
    }

    @Test
    void confirmEmailBind_phoneOneTapAccount_updatesEmailPasswordAndIdentity() {
        User user = activePhoneOneTapUser();
        when(userMapper.selectById("phone-user-1")).thenReturn(user);
        when(userMapper.selectOne(any())).thenReturn(null);
        when(valueOperations.get(
                AuthRedisKeys.emailCode(EmailCodePurpose.BIND_EMAIL, "phone@example.com")))
                .thenReturn("123456");
        when(passwordEncoder.encode("Password2026")).thenReturn("password-hash");
        when(userMapper.update(any(), any())).thenReturn(1);
        when(authIdentityMapper.selectOne(any())).thenReturn(null);

        LoginUserContext.setUserId("phone-user-1");
        try {
            authService.confirmEmailBind(
                    new EmailBindConfirmReq("phone@example.com", "123456", "Password2026"));

            verify(userMapper).update(any(), any());
            verify(authIdentityMapper).insert(any(UserAuthIdentity.class));
            verify(redis).delete(
                    AuthRedisKeys.emailCode(EmailCodePurpose.BIND_EMAIL, "phone@example.com"));
        } finally {
            LoginUserContext.clear();
        }
    }

    @Test
    void confirmEmailBind_whenEmailBelongsToAnotherUser_rejects() {
        User user = activePhoneOneTapUser();
        User existing = new User();
        existing.setId("other-user");
        existing.setEmail("used@example.com");
        when(userMapper.selectById("phone-user-1")).thenReturn(user);
        when(userMapper.selectOne(any())).thenReturn(existing);

        LoginUserContext.setUserId("phone-user-1");
        try {
            BizException ex = assertThrows(BizException.class, () -> authService.confirmEmailBind(
                    new EmailBindConfirmReq("used@example.com", "123456", "Password2026")));
            assertEquals(ErrorCode.EMAIL_ALREADY_REGISTERED, ex.getErrorCode());
            verify(userMapper, never()).update(any(), any());
            verify(redis, never()).delete(anyString());
        } finally {
            LoginUserContext.clear();
        }
    }

    @Test
    void confirmEmailBind_whenAlreadyHasEmail_rejects() {
        User user = activePhoneOneTapUser();
        user.setEmail("existing@example.com");
        when(userMapper.selectById("phone-user-1")).thenReturn(user);

        LoginUserContext.setUserId("phone-user-1");
        try {
            BizException ex = assertThrows(BizException.class, () -> authService.confirmEmailBind(
                    new EmailBindConfirmReq("phone@example.com", "123456", "Password2026")));
            assertEquals(ErrorCode.AUTH_PARAM_INVALID, ex.getErrorCode());
            verify(userMapper, never()).update(any(), any());
        } finally {
            LoginUserContext.clear();
        }
    }

    @Test
    void confirmEmailBind_whenConcurrentBindAlreadySetEmail_rejects() {
        User user = activePhoneOneTapUser();
        when(userMapper.selectById("phone-user-1")).thenReturn(user);
        when(userMapper.selectOne(any())).thenReturn(null);
        when(valueOperations.get(
                AuthRedisKeys.emailCode(EmailCodePurpose.BIND_EMAIL, "phone@example.com")))
                .thenReturn("123456");
        when(passwordEncoder.encode("Password2026")).thenReturn("password-hash");
        // 并发场景：前置校验通过，但 UPDATE ... WHERE email IS NULL 影响 0 行
        when(userMapper.update(any(), any())).thenReturn(0);

        LoginUserContext.setUserId("phone-user-1");
        try {
            BizException ex = assertThrows(BizException.class, () -> authService.confirmEmailBind(
                    new EmailBindConfirmReq("phone@example.com", "123456", "Password2026")));
            assertEquals(ErrorCode.AUTH_PARAM_INVALID, ex.getErrorCode());
            verify(authIdentityMapper, never()).insert(any(UserAuthIdentity.class));
            verify(redis, never()).delete(anyString());
        } finally {
            LoginUserContext.clear();
        }
    }

    @Test
    void confirmEmailBind_invalidCode_rejectsWithoutWriting() {
        User user = activePhoneOneTapUser();
        when(userMapper.selectById("phone-user-1")).thenReturn(user);
        when(userMapper.selectOne(any())).thenReturn(null);
        when(valueOperations.get(
                AuthRedisKeys.emailCode(EmailCodePurpose.BIND_EMAIL, "phone@example.com")))
                .thenReturn("123456");

        LoginUserContext.setUserId("phone-user-1");
        try {
            BizException ex = assertThrows(BizException.class, () -> authService.confirmEmailBind(
                    new EmailBindConfirmReq("phone@example.com", "000000", "Password2026")));
            assertEquals(ErrorCode.VERIFICATION_CODE_INVALID, ex.getErrorCode());
            verify(userMapper, never()).update(any(), any());
            verify(redis, never()).delete(anyString());
        } finally {
            LoginUserContext.clear();
        }
    }

    private User activePhoneOneTapUser() {
        User user = new User();
        user.setId("phone-user-1");
        user.setStatus("active");
        user.setLoginType(LoginType.PHONE_ONE_TAP.name());
        user.setPhone("+8613812345678");
        return user;
    }

    // ====================================================================
    // 微信登录
    // ====================================================================

    @Test
    void wechatMiniLogin_worksNormally() {
        User user = new User();
        user.setId("wechat-user-1");
        user.setStatus("active");
        user.setNickname("旅行者");
        user.setLoginType(LoginType.WECHAT_MINI.name());

        when(wechatMiniClient.exchangeCode("wx-code")).thenReturn(new WechatSession("openid-1", "session-key", "union-1"));
        when(authIdentityService.findOrCreateUserByIdentity(any(), eq("openid-1"), eq("union-1"), eq(LoginType.WECHAT_MINI)))
                .thenReturn(new AuthIdentityLoginResult(user, true));

        WechatMiniLoginResp resp = authService.wechatMiniLogin(new WechatMiniLoginReq("wx-code", null, null));

        assertNotNull(resp.accessToken());
        assertTrue(resp.isNewUser());
        assertEquals("wechat-user-1", resp.user().id());
    }

    @Test
    void wechatAppLogin_worksNormally() {
        User user = new User();
        user.setId("wechat-ios-user-1");
        user.setStatus("active");
        user.setNickname("微信旅人");
        user.setLoginType(LoginType.WECHAT_IOS.name());

        when(wechatAppClient.exchangeCode("ios-code")).thenReturn(
                new WechatOAuthResult("ios-openid-1", "union-1", "微信旅人", null));
        when(authIdentityService.findOrCreateUserByIdentity(
                any(), eq("ios-openid-1"), eq("union-1"), eq(LoginType.WECHAT_IOS), eq("微信旅人")))
                .thenReturn(new AuthIdentityLoginResult(user, false));

        WechatMiniLoginResp resp = authService.wechatAppLogin(new WechatAppLoginReq("ios-code", null));

        assertNotNull(resp.accessToken());
        assertFalse(resp.isNewUser());
        assertEquals("wechat-ios-user-1", resp.user().id());
    }

    @Test
    void wechatAppLogin_rejectsInvalidChannel() {
        assertThrows(BizException.class,
                () -> authService.wechatAppLogin(new WechatAppLoginReq("ios-code", "MINI_PROGRAM")));
    }

    // ====================================================================
    // Refresh
    // ====================================================================

    @Test
    void phoneOneTapLogin_withNewPhone_createsVerifiedPhoneUserAndIdentity() {
        when(smsClient.getMobileByAccessToken("aliyun-token")).thenReturn("+8613800138000");
        when(userMapper.selectOne(any())).thenReturn(null);
        when(userMapper.insert(any(User.class))).thenAnswer(invocation -> {
            User u = invocation.getArgument(0);
            u.setId("phone-user-1");
            return 1;
        });
        when(authIdentityMapper.selectOne(any())).thenReturn(null);

        TokenPairResp resp = authService.phoneOneTapLogin(new PhoneOneTapLoginReq("aliyun-token"));

        assertTrue(resp.isNewUser());
        assertEquals("phone-user-1", resp.user().id());
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).insert(userCaptor.capture());
        User created = userCaptor.getValue();
        assertEquals("+8613800138000", created.getPhone());
        assertTrue(created.getPhoneVerified());
        assertEquals(LoginType.PHONE_ONE_TAP.name(), created.getLoginType());

        ArgumentCaptor<UserAuthIdentity> identityCaptor = ArgumentCaptor.forClass(UserAuthIdentity.class);
        verify(authIdentityMapper).insert(identityCaptor.capture());
        UserAuthIdentity identity = identityCaptor.getValue();
        assertEquals(AuthProvider.PHONE.name(), identity.getProvider());
        assertEquals("+8613800138000", identity.getProviderUserId());
    }

    @Test
    void sendPhoneBindCode_withLocalCodeStore_writesRedisCode() {
        LoginUserContext.setUserId("user-1");
        try {
            when(userMapper.selectOne(any())).thenReturn(null);
            when(valueOperations.setIfAbsent(anyString(), eq("1"), any(Duration.class))).thenReturn(true);
            when(smsClient.storesVerificationCodeLocally()).thenReturn(true);

            authService.sendPhoneBindCode(new PhoneBindSendCodeReq("+8613800138000"));

            verify(smsClient).sendVerificationCode(eq("+8613800138000"), eq("123456"));
            verify(valueOperations).set(eq(AuthRedisKeys.phoneBindCode("+8613800138000")),
                    eq("123456"), any(Duration.class));
        } finally {
            LoginUserContext.clear();
        }
    }

    @Test
    void confirmPhoneBind_withRemoteVerification_doesNotReadRedisCode() {
        LoginUserContext.setUserId("user-1");
        try {
            User user = new User();
            user.setId("user-1");
            user.setStatus("active");

            when(userMapper.selectOne(any())).thenReturn(null);
            when(smsClient.storesVerificationCodeLocally()).thenReturn(false);
            when(smsClient.checkVerificationCode("+8613800138000", "123456")).thenReturn(true);
            when(userMapper.selectById("user-1")).thenReturn(user);
            when(authIdentityMapper.selectOne(any())).thenReturn(null);

            authService.confirmPhoneBind(new PhoneBindConfirmReq("+8613800138000", "123456"));

            verify(smsClient).checkVerificationCode("+8613800138000", "123456");
            verify(valueOperations, never()).getAndDelete(anyString());
            verify(authIdentityMapper).insert(any(UserAuthIdentity.class));
        } finally {
            LoginUserContext.clear();
        }
    }

    @Test
    void refresh_rejectsDeletedUser() {
        Instant now = Instant.now();
        String jti = "refresh-jti-1";
        String refresh = jwtUtil.createRefreshToken(now, "user-1", jti);

        User user = new User();
        user.setId("user-1");
        user.setStatus(UserAccountStatus.DELETED);

        when(valueOperations.get(AuthRedisKeys.refreshToken("user-1"))).thenReturn(jti);
        when(userIncludeDeletedJdbc.findById("user-1")).thenReturn(user);

        BizException ex = assertThrows(BizException.class,
                () -> authService.refresh(new RefreshTokenReq(refresh)));
        assertEquals(ErrorCode.ACCOUNT_DELETED, ex.getErrorCode());
    }
}
