package com.tracci.module.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.tracci.common.context.LoginUserContext;
import com.tracci.common.exception.AuthException;
import com.tracci.common.exception.BizException;
import com.tracci.common.response.ErrorCode;
import com.tracci.common.support.UserAccountStatus;
import com.tracci.common.util.JwtUtil;
import com.tracci.config.AuthFeatureProperties;
import com.tracci.config.TestAccountProperties;
import com.tracci.integration.cos.FileUrlResolver;
import com.tracci.integration.email.EmailClient;
import com.tracci.integration.sms.SmsClient;
import com.tracci.integration.wechat.WechatAppClient;
import com.tracci.integration.wechat.WechatMiniClient;
import com.tracci.integration.wechat.WechatOAuthResult;
import com.tracci.integration.wechat.WechatSession;
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
import com.tracci.module.auth.dto.response.AccessTokenRefreshResp;
import com.tracci.module.auth.dto.response.EmailCodeSendResp;
import com.tracci.module.auth.dto.response.LoginUserBriefResp;
import com.tracci.module.auth.dto.response.SmsSendResp;
import com.tracci.module.auth.dto.response.TokenPairResp;
import com.tracci.module.auth.dto.response.WechatMiniLoginResp;
import com.tracci.module.auth.entity.UserAuthIdentity;
import com.tracci.module.auth.enums.AuthProvider;
import com.tracci.module.auth.enums.EmailCodePurpose;
import com.tracci.module.auth.enums.LoginType;
import com.tracci.module.auth.repository.UserAuthIdentityMapper;
import com.tracci.module.auth.support.AuthRedisKeys;
import com.tracci.module.file.entity.File;
import com.tracci.module.file.repository.FileMapper;
import com.tracci.module.user.entity.User;
import com.tracci.module.user.repository.UserIncludeDeletedJdbc;
import com.tracci.module.user.repository.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthServiceImpl implements AuthService {

    private static final int EMAIL_CODE_TTL_SECONDS = 300;
    private static final int EMAIL_COOLDOWN_SECONDS = 60;
    private static final int LOGIN_FAIL_WINDOW_MINUTES = 15;
    private static final int LOGIN_FAIL_MAX = 5;

    private final UserMapper userMapper;
    private final UserIncludeDeletedJdbc userIncludeDeletedJdbc;
    private final StringRedisTemplate redis;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;
    private final SmsClient smsClient;
    private final WechatMiniClient wechatMiniClient;
    private final WechatAppClient wechatAppClient;
    private final AuthIdentityService authIdentityService;
    private final UserAuthIdentityMapper authIdentityMapper;
    private final FileMapper fileMapper;
    private final FileUrlResolver fileUrlResolver;
    private final EmailClient emailClient;
    private final TestAccountProperties testAccountProperties;
    private final AuthFeatureProperties authFeatureProperties;

    @Value("${tracci.integration.sms.mock:true}")
    private boolean smsMock;
    @Value("${tracci.integration.sms.mock-fixed-code:}")
    private String smsMockFixedCode;

    // ========================================================================
    // 邮箱注册
    // ========================================================================

    @Override
    @Transactional
    public TokenPairResp register(RegisterReq req) {
        String email = normalizeEmail(req.email());

        // 检查邮箱是否已注册
        User exists = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getEmail, email));
        if (exists != null) {
            throw new BizException(ErrorCode.EMAIL_ALREADY_REGISTERED);
        }

        Instant now = Instant.now();
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(req.password()));
        user.setNickname(StringUtils.hasText(req.nickname()) ? req.nickname().trim() : null);
        user.setStatus("active");
        user.setLoginType(LoginType.EMAIL.name());
        user.setPhoneVerified(false);
        user.setLastLoginAt(com.tracci.common.util.DateTimeUtil.nowUtc());
        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException ex) {
            throw new BizException(ErrorCode.EMAIL_ALREADY_REGISTERED);
        }

        // 创建 EMAIL identity
        authIdentityService.createEmailIdentity(user.getId(), email);

        return issueTokens(user, now, true);
    }

    // ========================================================================
    // 邮箱登录
    // ========================================================================

    @Override
    @Transactional
    public TokenPairResp login(LoginReq req) {
        String identifier = req.email().trim();

        // 测试账号白名单检查
        TestAccountProperties.Account testAccount = findTestAccount(identifier);
        if (testAccount != null) {
            if (!req.password().equals(testAccount.getPassword())) {
                throw new BizException(ErrorCode.EMAIL_OR_PASSWORD_INVALID);
            }
            return loginAsTestAccount(testAccount);
        }

        // 普通邮箱登录
        String email = normalizeEmail(identifier);
        String failKey = AuthRedisKeys.loginFail(email);
        String failCountStr = redis.opsForValue().get(failKey);
        int failCount = failCountStr == null ? 0 : Integer.parseInt(failCountStr);
        if (failCount >= LOGIN_FAIL_MAX) {
            Long ttl = redis.getExpire(failKey);
            long sec = ttl != null && ttl > 0 ? ttl : LOGIN_FAIL_WINDOW_MINUTES * 60L;
            throw new BizException(
                    ErrorCode.SEND_CODE_TOO_FREQUENT,
                    "账号因连续错误已被临时锁定，请 " + sec + " 秒后再试");
        }

        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getEmail, email));
        Instant now = Instant.now();

        if (user == null
                || user.getPasswordHash() == null
                || !passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            Long c = redis.opsForValue().increment(failKey);
            if (c != null && c == 1L) {
                redis.expire(failKey, Duration.ofMinutes(LOGIN_FAIL_WINDOW_MINUTES));
            }
            throw new BizException(ErrorCode.EMAIL_OR_PASSWORD_INVALID);
        }

        redis.delete(failKey);
        UserAccountStatus.requireActive(user);
        touchLastLogin(user, LoginType.EMAIL);
        return issueTokens(user, now, false);
    }

    // ========================================================================
    // 修改密码（已登录，需旧密码）
    // ========================================================================

    @Override
    @Transactional
    public void changePassword(ChangePasswordReq req) {
        String userId = LoginUserContext.requireUserId();
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.USER_NOT_FOUND);
        }
        UserAccountStatus.requireActive(user);
        if (user.getPasswordHash() == null) {
            throw new BizException(ErrorCode.PASSWORD_NOT_SET);
        }
        if (!passwordEncoder.matches(req.oldPassword(), user.getPasswordHash())) {
            throw new BizException(ErrorCode.EMAIL_OR_PASSWORD_INVALID, "旧密码错误");
        }
        if (passwordEncoder.matches(req.newPassword(), user.getPasswordHash())) {
            throw new BizException(ErrorCode.AUTH_PARAM_INVALID, "新密码不能与旧密码相同");
        }
        user.setPasswordHash(passwordEncoder.encode(req.newPassword()));
        userMapper.updateById(user);
        redis.delete(AuthRedisKeys.refreshToken(userId));
    }

    // ========================================================================
    // 发送重置密码邮箱验证码
    // ========================================================================

    @Override
    public EmailCodeSendResp sendResetPasswordEmailCode(SendResetPasswordEmailCodeReq req) {
        if (!authFeatureProperties.isEmailResetPasswordEnabled()) {
            throw new BizException(ErrorCode.AUTH_PROVIDER_DISABLED, "邮箱重置密码功能暂未开放");
        }

        String email = normalizeEmail(req.email());
        EmailCodePurpose purpose = EmailCodePurpose.RESET_PASSWORD;

        // 限流检查
        String cooldownKey = AuthRedisKeys.emailCooldown(purpose, email);
        Boolean locked = redis.opsForValue().setIfAbsent(cooldownKey, "1", Duration.ofSeconds(EMAIL_COOLDOWN_SECONDS));
        if (!Boolean.TRUE.equals(locked)) {
            throw new BizException(ErrorCode.SEND_CODE_TOO_FREQUENT);
        }

        // 无论邮箱是否存在，都返回相同响应（防止枚举）
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getEmail, email));

        try {
            if (user != null) {
                // 生成并存储验证码
                String code = generateEmailCode();
                redis.opsForValue().set(
                        AuthRedisKeys.emailCode(purpose, email),
                        code,
                        Duration.ofSeconds(EMAIL_CODE_TTL_SECONDS));
                emailClient.sendVerificationCode(email, code, purpose);
            }
            // 邮箱不存在时不发送，但仍返回相同响应
        } catch (RuntimeException ex) {
            redis.delete(cooldownKey);
            throw ex;
        }

        return new EmailCodeSendResp(EMAIL_CODE_TTL_SECONDS, EMAIL_COOLDOWN_SECONDS);
    }

    // ========================================================================
    // 邮箱验证码重置密码
    // ========================================================================

    @Override
    @Transactional
    public void resetPasswordByEmailCode(ResetPasswordReq req) {
        if (!authFeatureProperties.isEmailResetPasswordEnabled()) {
            throw new BizException(ErrorCode.AUTH_PROVIDER_DISABLED, "邮箱重置密码功能暂未开放");
        }

        String email = normalizeEmail(req.email());
        EmailCodePurpose purpose = EmailCodePurpose.RESET_PASSWORD;

        // 校验验证码
        String codeKey = AuthRedisKeys.emailCode(purpose, email);
        String storedCode = redis.opsForValue().get(codeKey);
        if (storedCode == null || !storedCode.equals(req.code())) {
            throw new BizException(ErrorCode.VERIFICATION_CODE_INVALID);
        }

        // 查找用户
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getEmail, email));
        if (user == null) {
            // 用户不存在时统一返回验证码错误，不暴露邮箱是否存在
            throw new BizException(ErrorCode.VERIFICATION_CODE_INVALID);
        }

        // 更新密码
        user.setPasswordHash(passwordEncoder.encode(req.newPassword()));
        userMapper.update(null, new UpdateWrapper<User>()
                .eq("id", user.getId())
                .set("password_hash", user.getPasswordHash()));

        // 删除验证码
        redis.delete(codeKey);

        // 撤销所有 RefreshToken
        redis.delete(AuthRedisKeys.refreshToken(user.getId()));

        log.info("Password reset via email code userId={}", user.getId());
    }

    // ========================================================================
    // 手机号一键登录账号绑定邮箱
    // ========================================================================

    @Override
    public EmailCodeSendResp sendEmailBindCode(EmailBindSendCodeReq req) {
        User user = requirePhoneOneTapUserWithoutEmail();
        String email = normalizeEmail(req.email());
        User existing = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getEmail, email));
        if (existing != null && !user.getId().equals(existing.getId())) {
            throw new BizException(ErrorCode.EMAIL_ALREADY_REGISTERED);
        }

        EmailCodePurpose purpose = EmailCodePurpose.BIND_EMAIL;
        String cooldownKey = AuthRedisKeys.emailCooldown(purpose, email);
        Boolean locked = redis.opsForValue().setIfAbsent(
                cooldownKey, "1", Duration.ofSeconds(EMAIL_COOLDOWN_SECONDS));
        if (!Boolean.TRUE.equals(locked)) {
            throw new BizException(ErrorCode.SEND_CODE_TOO_FREQUENT);
        }

        String code = generateEmailCode();
        try {
            redis.opsForValue().set(
                    AuthRedisKeys.emailCode(purpose, email),
                    code,
                    Duration.ofSeconds(EMAIL_CODE_TTL_SECONDS));
            emailClient.sendVerificationCode(email, code, purpose);
        } catch (RuntimeException ex) {
            redis.delete(cooldownKey);
            redis.delete(AuthRedisKeys.emailCode(purpose, email));
            throw ex;
        }
        return new EmailCodeSendResp(EMAIL_CODE_TTL_SECONDS, EMAIL_COOLDOWN_SECONDS);
    }

    @Override
    @Transactional
    public void confirmEmailBind(EmailBindConfirmReq req) {
        User user = requirePhoneOneTapUserWithoutEmail();
        String email = normalizeEmail(req.email());
        User existing = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getEmail, email));
        if (existing != null && !user.getId().equals(existing.getId())) {
            throw new BizException(ErrorCode.EMAIL_ALREADY_REGISTERED);
        }

        // 先校验再删除：DB 失败时验证码仍可重试，避免并发/瞬时故障直接作废
        String codeKey = AuthRedisKeys.emailCode(EmailCodePurpose.BIND_EMAIL, email);
        String storedCode = redis.opsForValue().get(codeKey);
        if (storedCode == null || !storedCode.equals(req.code())) {
            throw new BizException(ErrorCode.VERIFICATION_CODE_INVALID);
        }

        LocalDateTime now = com.tracci.common.util.DateTimeUtil.nowUtc();
        String passwordHash = passwordEncoder.encode(req.password());
        try {
            // email IS NULL 防止同用户并发 confirm 时 last-write-wins 覆盖邮箱/密码
            Integer updated = userMapper.update(null, new UpdateWrapper<User>()
                    .eq("id", user.getId())
                    .isNull("email")
                    .set("email", email)
                    .set("password_hash", passwordHash));
            if (updated == null || updated == 0) {
                throw new BizException(ErrorCode.AUTH_PARAM_INVALID, "当前账号已设置登录邮箱");
            }
            upsertEmailIdentity(user.getId(), email, now);
            redis.delete(codeKey);
        } catch (DuplicateKeyException ex) {
            throw new BizException(ErrorCode.EMAIL_ALREADY_REGISTERED);
        }

        log.info("Email bound to phone one-tap account userId={} email={}", user.getId(), maskEmail(email));
    }

    private User requirePhoneOneTapUserWithoutEmail() {
        String userId = LoginUserContext.requireUserId();
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.USER_NOT_FOUND);
        }
        UserAccountStatus.requireActive(user);
        if (!LoginType.PHONE_ONE_TAP.name().equals(user.getLoginType())) {
            throw new BizException(ErrorCode.AUTH_PARAM_INVALID, "仅手机号一键登录账号可绑定邮箱");
        }
        if (StringUtils.hasText(user.getEmail())) {
            throw new BizException(ErrorCode.AUTH_PARAM_INVALID, "当前账号已设置登录邮箱");
        }
        return user;
    }

    /*
     * FUTURE（待产品确认）：邮箱换绑实现钩子。
     *
     * 旧版草案曾允许已登录用户直接用新邮箱验证码覆盖 email，
     * 但没有形成“原邮箱/当前密码校验、会话撤销、identity 原子替换”的完整安全契约，
     * 因此不保留为可执行分支。后续确认方案时，可从此处接入独立换绑流程，
     * 不应删除或放宽 requirePhoneOneTapUserWithoutEmail() 的首次绑定边界。
     */

    private void upsertEmailIdentity(String userId, String email, LocalDateTime now) {
        UserAuthIdentity identity = authIdentityMapper.selectOne(
                new LambdaQueryWrapper<UserAuthIdentity>()
                        .eq(UserAuthIdentity::getUserId, userId)
                        .eq(UserAuthIdentity::getProvider, AuthProvider.EMAIL.name()));
        if (identity == null) {
            identity = new UserAuthIdentity();
            identity.setUserId(userId);
            identity.setProvider(AuthProvider.EMAIL.name());
            identity.setProviderUserId(email);
            identity.setEmail(email);
            identity.setStatus("active");
            identity.setBoundAt(now);
            authIdentityMapper.insert(identity);
            return;
        }
        identity.setProviderUserId(email);
        identity.setEmail(email);
        identity.setStatus("active");
        authIdentityMapper.updateById(identity);
    }

    // ========================================================================
    // iOS 本机号码一键登录
    // ========================================================================

    @Override
    @Transactional
    public TokenPairResp phoneOneTapLogin(PhoneOneTapLoginReq req) {
        String phone = smsClient.getMobileByAccessToken(req.accessToken());
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getPhone, phone));
        boolean isNew = false;
        Instant nowInstant = Instant.now();
        LocalDateTime now = com.tracci.common.util.DateTimeUtil.nowUtc();

        if (user == null) {
            isNew = true;
            user = new User();
            user.setPhone(phone);
            user.setNickname("旅人");
            user.setStatus("active");
            user.setLoginType(LoginType.PHONE_ONE_TAP.name());
            user.setPhoneVerified(true);
            user.setPhoneVerifiedAt(now);
            user.setLastLoginAt(now);
            try {
                userMapper.insert(user);
            } catch (DuplicateKeyException ex) {
                user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getPhone, phone));
                isNew = false;
                if (user == null) {
                    throw ex;
                }
                UserAccountStatus.requireActive(user);
                touchLastLogin(user, LoginType.PHONE_ONE_TAP);
            }
        } else {
            UserAccountStatus.requireActive(user);
            ensurePhoneVerified(user, now);
            touchLastLogin(user, LoginType.PHONE_ONE_TAP);
        }

        upsertPhoneIdentity(user.getId(), phone, now);
        return issueTokens(user, nowInstant, isNew);
    }

    // ========================================================================
    // 手机号绑定/解绑
    // ========================================================================

    @Override
    public SmsSendResp sendPhoneBindCode(PhoneBindSendCodeReq req) {
        String phone = req.phone();
        String userId = LoginUserContext.requireUserId();

        // 检查手机号是否已被其他用户绑定（当前用户自己的手机号允许重新发送）
        User existing = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getPhone, phone));
        if (existing != null && !existing.getId().equals(userId)) {
            throw new BizException(ErrorCode.PHONE_ALREADY_BOUND);
        }

        // 限流检查
        String cooldown = AuthRedisKeys.phoneBindCooldown(phone);
        Boolean locked = redis.opsForValue().setIfAbsent(cooldown, "1", Duration.ofSeconds(EMAIL_COOLDOWN_SECONDS));
        if (!Boolean.TRUE.equals(locked)) {
            throw new BizException(ErrorCode.SEND_CODE_TOO_FREQUENT);
        }

        // 发送验证码
        String code = generateSmsCode();
        try {
            smsClient.sendVerificationCode(phone, code);
            if (smsClient.storesVerificationCodeLocally()) {
                redis.opsForValue().set(AuthRedisKeys.phoneBindCode(phone), code,
                        Duration.ofSeconds(EMAIL_CODE_TTL_SECONDS));
            }
        } catch (RuntimeException ex) {
            redis.delete(cooldown);
            throw ex;
        }
        return new SmsSendResp(EMAIL_CODE_TTL_SECONDS, EMAIL_COOLDOWN_SECONDS);
    }

    @Override
    @Transactional
    public void confirmPhoneBind(PhoneBindConfirmReq req) {
        String userId = LoginUserContext.requireUserId();
        String phone = req.phone();

        // 先检查手机号是否已被其他用户绑定（在消费验证码之前，避免验证码浪费）
        User existing = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getPhone, phone));
        if (existing != null && !existing.getId().equals(userId)) {
            throw new BizException(ErrorCode.PHONE_ALREADY_BOUND);
        }

        verifyPhoneBindCode(phone, req.code());

        // 更新用户的手机号
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.USER_NOT_FOUND);
        }
        UserAccountStatus.requireActive(user);

        bindPhoneToUser(userId, phone, com.tracci.common.util.DateTimeUtil.nowUtc());

        log.info("Phone bound userId={} phone={}", userId, phone);
    }

    @Override
    @Transactional
    public void unbindPhone() {
        String userId = LoginUserContext.requireUserId();

        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.USER_NOT_FOUND);
        }
        UserAccountStatus.requireActive(user);

        if (!StringUtils.hasText(user.getPhone())) {
            throw new BizException(ErrorCode.PHONE_NOT_BOUND);
        }

        // 检查用户是否有其他可用的登录方式（邮箱+密码 或 微信）
        boolean hasEmailLogin = StringUtils.hasText(user.getEmail()) && user.getPasswordHash() != null;
        boolean hasWechatIdentity = authIdentityMapper.selectCount(
                new LambdaQueryWrapper<UserAuthIdentity>()
                        .eq(UserAuthIdentity::getUserId, userId)
                        .in(UserAuthIdentity::getProvider,
                                AuthProvider.WECHAT_MINI.name(), AuthProvider.WECHAT_APP.name())) > 0;
        if (!hasEmailLogin && !hasWechatIdentity) {
            throw new BizException(ErrorCode.AUTH_PARAM_INVALID, "解绑手机号后将无法登录，请先绑定邮箱密码或微信");
        }

        String phone = user.getPhone();

        // 清空用户的手机号
        userMapper.update(null, new UpdateWrapper<User>()
                .eq("id", userId)
                .set("phone", null)
                .set("phone_verified", false)
                .set("phone_verified_at", null));

        // 删除 PHONE identity
        authIdentityMapper.delete(
                new LambdaQueryWrapper<UserAuthIdentity>()
                        .eq(UserAuthIdentity::getUserId, userId)
                        .eq(UserAuthIdentity::getProvider, AuthProvider.PHONE.name()));

        log.info("Phone unbound userId={} phone={}", userId, phone);
    }

    private void verifyPhoneBindCode(String phone, String code) {
        if (smsClient.storesVerificationCodeLocally()) {
            String codeKey = AuthRedisKeys.phoneBindCode(phone);
            String storedCode = redis.opsForValue().getAndDelete(codeKey);
            if (storedCode == null || !storedCode.equals(code)) {
                throw new BizException(ErrorCode.VERIFICATION_CODE_INVALID);
            }
            return;
        }
        if (!smsClient.checkVerificationCode(phone, code)) {
            throw new BizException(ErrorCode.VERIFICATION_CODE_INVALID);
        }
    }

    private void ensurePhoneVerified(User user, LocalDateTime now) {
        if (Boolean.TRUE.equals(user.getPhoneVerified())) {
            return;
        }
        user.setPhoneVerified(true);
        user.setPhoneVerifiedAt(now);
        userMapper.update(null, new UpdateWrapper<User>()
                .eq("id", user.getId())
                .set("phone_verified", true)
                .set("phone_verified_at", now));
    }

    private void bindPhoneToUser(String userId, String phone, LocalDateTime now) {
        try {
            userMapper.update(null, new UpdateWrapper<User>()
                    .eq("id", userId)
                    .set("phone", phone)
                    .set("phone_verified", true)
                    .set("phone_verified_at", now));
        } catch (DuplicateKeyException ex) {
            throw new BizException(ErrorCode.PHONE_ALREADY_BOUND);
        }
        upsertPhoneIdentity(userId, phone, now);
    }

    private void upsertPhoneIdentity(String userId, String phone, LocalDateTime now) {
        UserAuthIdentity identity = authIdentityMapper.selectOne(
                new LambdaQueryWrapper<UserAuthIdentity>()
                        .eq(UserAuthIdentity::getUserId, userId)
                        .eq(UserAuthIdentity::getProvider, AuthProvider.PHONE.name()));
        if (identity == null) {
            identity = new UserAuthIdentity();
            identity.setUserId(userId);
            identity.setProvider(AuthProvider.PHONE.name());
            identity.setProviderUserId(phone);
            identity.setPhone(phone);
            identity.setStatus("active");
            identity.setBoundAt(now);
            authIdentityMapper.insert(identity);
            return;
        }
        identity.setProviderUserId(phone);
        identity.setPhone(phone);
        authIdentityMapper.updateById(identity);
    }

    // ========================================================================
    // 微信登录（feature flag 控制）

    @Override
    @Transactional
    public WechatMiniLoginResp wechatMiniLogin(WechatMiniLoginReq req) {
        WechatSession session = wechatMiniClient.exchangeCode(req.code());
        Instant now = Instant.now();
        AuthIdentityLoginResult loginResult = authIdentityService.findOrCreateUserByIdentity(
                AuthProvider.WECHAT_MINI,
                session.openid(),
                session.unionid(),
                LoginType.WECHAT_MINI);
        return issueWechatLoginResp(loginResult.user(), now, loginResult.isNewUser(), LoginType.WECHAT_MINI);
    }

    @Override
    @Transactional
    public WechatMiniLoginResp wechatAppLogin(WechatAppLoginReq req) {
        if (StringUtils.hasText(req.channel()) && !"IOS".equalsIgnoreCase(req.channel())) {
            throw new BizException(ErrorCode.AUTH_PARAM_INVALID, "channel 仅支持 IOS");
        }
        log.info("Wechat app login request received channel=IOS");
        WechatOAuthResult oauth = wechatAppClient.exchangeCode(req.code());
        Instant now = Instant.now();
        AuthIdentityLoginResult loginResult = authIdentityService.findOrCreateUserByIdentity(
                AuthProvider.WECHAT_APP,
                oauth.openid(),
                oauth.unionid(),
                LoginType.WECHAT_IOS,
                oauth.nickname());
        log.info("Wechat app login completed userId={} isNewUser={} unionIdPresent={}",
                loginResult.user().getId(),
                loginResult.isNewUser(),
                StringUtils.hasText(oauth.unionid()));
        return issueWechatLoginResp(loginResult.user(), now, loginResult.isNewUser(), LoginType.WECHAT_IOS);
    }

    // ========================================================================
    // Refresh / Logout
    // ========================================================================

    @Override
    @Transactional(readOnly = true)
    public AccessTokenRefreshResp refresh(RefreshTokenReq req) {
        JwtUtil.RefreshTokenPayload payload;
        try {
            payload = jwtUtil.parseRefreshToken(req.refreshToken());
        } catch (Exception ex) {
            throw new AuthException(ErrorCode.TOKEN_INVALID);
        }
        String redisJti = redis.opsForValue().get(AuthRedisKeys.refreshToken(payload.userId()));
        if (redisJti == null || !redisJti.equals(payload.jti())) {
            throw new AuthException(ErrorCode.TOKEN_INVALID);
        }
        User user = userIncludeDeletedJdbc.findById(payload.userId());
        if (user == null) {
            throw new AuthException(ErrorCode.TOKEN_INVALID);
        }
        UserAccountStatus.requireActive(user);
        Instant now = Instant.now();
        String access = jwtUtil.createAccessToken(now, user.getId());
        Instant exp = now.plusSeconds(jwtUtil.getAccessTokenExpireSeconds());
        return new AccessTokenRefreshResp(access, exp.toString());
    }

    @Override
    public void logout() {
        String userId = LoginUserContext.requireUserId();
        redis.delete(AuthRedisKeys.refreshToken(userId));
    }

    // ========================================================================
    // 内部方法
    // ========================================================================

    /**
     * 测试账号登录：查找或自动创建用户
     */
    private TokenPairResp loginAsTestAccount(TestAccountProperties.Account account) {
        String email = account.getEmail().toLowerCase().trim();
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getEmail, email));
        Instant now = Instant.now();
        boolean isNew = false;

        if (user == null) {
            // 自动创建测试账号用户
            isNew = true;
            user = new User();
            user.setEmail(email);
            user.setPasswordHash(passwordEncoder.encode(account.getPassword()));
            user.setNickname("管理员");
            user.setStatus("active");
            user.setLoginType(LoginType.EMAIL.name());
            user.setPhoneVerified(false);
            user.setLastLoginAt(com.tracci.common.util.DateTimeUtil.nowUtc());
            try {
                userMapper.insert(user);
            } catch (DuplicateKeyException ex) {
                user = userMapper.selectOne(
                        new LambdaQueryWrapper<User>().eq(User::getEmail, email));
                isNew = false;
                if (user == null) {
                    throw ex;
                }
            }
            // 创建 EMAIL identity
            authIdentityService.createEmailIdentity(user.getId(), email);
            log.info("Test account auto-created userId={} email={}", user.getId(), email);
        } else {
            UserAccountStatus.requireActive(user);
            touchLastLogin(user, LoginType.EMAIL);
        }

        return issueTokens(user, now, isNew);
    }

    private TestAccountProperties.Account findTestAccount(String identifier) {
        if (!testAccountProperties.isEnabled() || !StringUtils.hasText(identifier)) {
            return null;
        }
        String lower = identifier.trim().toLowerCase();
        for (TestAccountProperties.Account account : testAccountProperties.getAccounts()) {
            if (account.getLoginId() != null && account.getLoginId().toLowerCase().equals(lower)) {
                return account;
            }
        }
        return null;
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase();
    }

    /** 日志脱敏：保留 local 首尾字符，避免明文邮箱进入日志。 */
    private static String maskEmail(String email) {
        if (!StringUtils.hasText(email) || !email.contains("@")) {
            return "***";
        }
        int at = email.indexOf('@');
        String local = email.substring(0, at);
        String domain = email.substring(at);
        if (local.length() <= 1) {
            return "*" + domain;
        }
        if (local.length() == 2) {
            return local.charAt(0) + "*" + domain;
        }
        return local.charAt(0) + "***" + local.charAt(local.length() - 1) + domain;
    }

    private TokenPairResp issueTokens(User user, Instant now, boolean isNewUser) {
        String access = jwtUtil.createAccessToken(now, user.getId());
        String jti = java.util.UUID.randomUUID().toString();
        String refresh = jwtUtil.createRefreshToken(now, user.getId(), jti);
        redis.opsForValue().set(
                AuthRedisKeys.refreshToken(user.getId()),
                jti,
                Duration.ofSeconds(jwtUtil.getRefreshTokenExpireSeconds()));
        Instant accessExp = now.plusSeconds(jwtUtil.getAccessTokenExpireSeconds());
        return new TokenPairResp(access, refresh, accessExp.toString(), toLoginUserBrief(user), isNewUser);
    }

    private WechatMiniLoginResp issueWechatLoginResp(User user, Instant now, boolean isNewUser, LoginType loginType) {
        if (!StringUtils.hasText(user.getLoginType())) {
            user.setLoginType(loginType.name());
        }
        String access = jwtUtil.createAccessToken(now, user.getId());
        String jti = java.util.UUID.randomUUID().toString();
        String refresh = jwtUtil.createRefreshToken(now, user.getId(), jti);
        redis.opsForValue().set(
                AuthRedisKeys.refreshToken(user.getId()),
                jti,
                Duration.ofSeconds(jwtUtil.getRefreshTokenExpireSeconds()));
        Instant accessExp = now.plusSeconds(jwtUtil.getAccessTokenExpireSeconds());
        return new WechatMiniLoginResp(
                access,
                refresh,
                "Bearer",
                jwtUtil.getAccessTokenExpireSeconds(),
                accessExp.toString(),
                toLoginUserBrief(user),
                isNewUser);
    }

    private LoginUserBriefResp toLoginUserBrief(User user) {
        return new LoginUserBriefResp(
                user.getId(),
                StringUtils.hasText(user.getEmail()) ? user.getEmail() : null,
                StringUtils.hasText(user.getNickname()) ? user.getNickname() : null,
                StringUtils.hasText(user.getAvatarFileId()) ? user.getAvatarFileId() : null,
                resolveAvatarUrl(user),
                StringUtils.hasText(user.getLoginType()) ? user.getLoginType() : null);
    }

    private String resolveAvatarUrl(User user) {
        if (!StringUtils.hasText(user.getAvatarFileId())) {
            return null;
        }
        File file = fileMapper.selectById(user.getAvatarFileId());
        if (file == null || "DELETED".equals(file.getStatus()) || !user.getId().equals(file.getUserId())) {
            return null;
        }
        return fileUrlResolver.resolve(file);
    }

    private void touchLastLogin(User user, LoginType loginType) {
        java.time.LocalDateTime now = com.tracci.common.util.DateTimeUtil.nowUtc();
        boolean setLoginType = !StringUtils.hasText(user.getLoginType());
        user.setLastLoginAt(now);
        if (setLoginType) {
            user.setLoginType(loginType.name());
        }
        userMapper.update(null, new UpdateWrapper<User>()
                .eq("id", user.getId())
                .set("last_login_at", now)
                .set(setLoginType, "login_type", loginType.name()));
    }

    private String generateSmsCode() {
        if (smsMock && StringUtils.hasText(smsMockFixedCode)) {
            return smsMockFixedCode;
        }
        return String.format("%06d", ThreadLocalRandom.current().nextInt(0, 1_000_000));
    }

    private String generateEmailCode() {
        return String.format("%06d", ThreadLocalRandom.current().nextInt(0, 1_000_000));
    }
}
