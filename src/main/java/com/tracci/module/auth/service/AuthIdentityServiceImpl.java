package com.tracci.module.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.tracci.common.exception.BizException;
import com.tracci.common.response.ErrorCode;
import com.tracci.common.support.UserAccountStatus;
import com.tracci.module.auth.entity.UserAuthIdentity;
import com.tracci.module.auth.enums.AuthProvider;
import com.tracci.module.auth.enums.LoginType;
import com.tracci.module.auth.repository.UserAuthIdentityIncludeDeletedJdbc;
import com.tracci.module.auth.repository.UserAuthIdentityMapper;
import com.tracci.common.util.DateTimeUtil;
import com.tracci.module.user.entity.User;
import com.tracci.module.user.repository.UserIncludeDeletedJdbc;
import com.tracci.module.user.repository.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthIdentityServiceImpl implements AuthIdentityService {

    private static final String DEFAULT_NICKNAME = "旅人";

    /** 微信 provider 列表，用于 unionId 跨端归并 */
    private static final List<String> WECHAT_PROVIDERS = Arrays.asList(
            AuthProvider.WECHAT_MINI.name(),
            AuthProvider.WECHAT_APP.name()
    );

    private final UserAuthIdentityMapper identityMapper;
    private final UserAuthIdentityIncludeDeletedJdbc identityIncludeDeletedJdbc;
    private final UserMapper userMapper;
    private final UserIncludeDeletedJdbc userIncludeDeletedJdbc;

    @Override
    @Transactional
    public AuthIdentityLoginResult findOrCreateUserByIdentity(
            AuthProvider provider,
            String providerUserId,
            String unionId,
            LoginType loginType) {
        return findOrCreateUserByIdentity(provider, providerUserId, unionId, loginType, null);
    }

    @Override
    @Transactional
    public AuthIdentityLoginResult findOrCreateUserByIdentity(
            AuthProvider provider,
            String providerUserId,
            String unionId,
            LoginType loginType,
            String nickname) {
        if (!StringUtils.hasText(providerUserId)) {
            throw new BizException(ErrorCode.WECHAT_LOGIN_FAILED);
        }

        // 1. 按 (provider, provider_user_id) 精确匹配
        UserAuthIdentity channelIdentity = findByProviderIdentity(provider, providerUserId);
        if (channelIdentity != null) {
            User user = userIncludeDeletedJdbc.findById(channelIdentity.getUserId());
            if (UserAccountStatus.isActive(user)) {
                updateIdentityUnionIdIfNeeded(channelIdentity, unionId);
                touchLastLogin(user, loginType);
                log.info("Auth identity login matched by provider userId={} provider={}",
                        user.getId(), provider);
                return new AuthIdentityLoginResult(user, false);
            }
            tombstoneIdentity(channelIdentity);
            log.info("Auth identity login skipped deleted-user binding identityId={} provider={}",
                    channelIdentity.getId(), provider);
        }

        // 2. 按 unionId 跨微信端归并
        if (StringUtils.hasText(unionId) && isWechatProvider(provider)) {
            UserAuthIdentity unionIdentity = findByWechatUnionId(unionId);
            if (unionIdentity != null) {
                User user = userIncludeDeletedJdbc.findById(unionIdentity.getUserId());
                if (UserAccountStatus.isActive(user)) {
                    attachIdentity(provider, providerUserId, unionId, user.getId());
                    touchLastLogin(user, loginType);
                    log.info("Auth identity login merged by unionId userId={} provider={}",
                            user.getId(), provider);
                    return new AuthIdentityLoginResult(user, false);
                }
                tombstoneIdentity(unionIdentity);
                log.info("Auth identity login skipped deleted-user union merge identityId={} provider={}",
                        unionIdentity.getId(), provider);
            }
        }

        // 3. 创建新用户
        return createUserWithIdentity(provider, providerUserId, unionId, loginType, nickname);
    }

    @Override
    @Transactional
    public void createEmailIdentity(String userId, String email) {
        String normalizedEmail = email.toLowerCase().trim();
        UserAuthIdentity identity = new UserAuthIdentity();
        identity.setUserId(userId);
        identity.setProvider(AuthProvider.EMAIL.name());
        identity.setProviderUserId(normalizedEmail);
        identity.setEmail(normalizedEmail);
        identity.setStatus("active");
        identity.setBoundAt(DateTimeUtil.nowUtc());
        try {
            identityMapper.insert(identity);
        } catch (DuplicateKeyException ex) {
            log.warn("Email identity already exists for email={} userId={}", normalizedEmail, userId);
        }
    }

    @Override
    public UserAuthIdentity findActiveIdentityByEmail(String email) {
        String normalizedEmail = email.toLowerCase().trim();
        return identityMapper.selectOne(new LambdaQueryWrapper<UserAuthIdentity>()
                .eq(UserAuthIdentity::getProvider, AuthProvider.EMAIL.name())
                .eq(UserAuthIdentity::getProviderUserId, normalizedEmail));
    }

    private AuthIdentityLoginResult createUserWithIdentity(
            AuthProvider provider,
            String providerUserId,
            String unionId,
            LoginType loginType,
            String nickname) {
        LocalDateTime now = DateTimeUtil.nowUtc();
        User user = new User();
        user.setNickname(resolveNickname(nickname));
        user.setStatus("active");
        user.setLoginType(loginType.name());
        user.setLastLoginAt(now);
        user.setPhoneVerified(false);
        userMapper.insert(user);

        attachIdentity(provider, providerUserId, unionId, user.getId());
        log.info("Auth identity login created new user userId={} provider={}",
                user.getId(), provider);
        return new AuthIdentityLoginResult(user, true);
    }

    private void attachIdentity(
            AuthProvider provider,
            String providerUserId,
            String unionId,
            String userId) {
        UserAuthIdentity identity = new UserAuthIdentity();
        identity.setUserId(userId);
        identity.setProvider(provider.name());
        identity.setProviderUserId(providerUserId);
        if (StringUtils.hasText(unionId)) {
            identity.setUnionId(unionId);
        }
        identity.setStatus("active");
        identity.setBoundAt(DateTimeUtil.nowUtc());
        try {
            identityMapper.insert(identity);
        } catch (DuplicateKeyException ex) {
            UserAuthIdentity exists = findByProviderIdentity(provider, providerUserId);
            if (exists == null) {
                throw ex;
            }
            if (!userId.equals(exists.getUserId())) {
                log.warn("Auth identity attach conflict provider={} requestedUserId={} existingUserId={}",
                        provider, userId, exists.getUserId());
                throw new BizException(ErrorCode.WECHAT_LOGIN_FAILED, "登录状态发生变化，请重试");
            }
            updateIdentityUnionIdIfNeeded(exists, unionId);
            log.info("Auth identity attach deduplicated userId={} provider={}",
                    exists.getUserId(), provider);
        }
    }

    private UserAuthIdentity findByProviderIdentity(AuthProvider provider, String providerUserId) {
        return identityMapper.selectOne(new LambdaQueryWrapper<UserAuthIdentity>()
                .eq(UserAuthIdentity::getProvider, provider.name())
                .eq(UserAuthIdentity::getProviderUserId, providerUserId));
    }

    /**
     * 按 unionId 在所有微信 provider 中查找 identity
     */
    private UserAuthIdentity findByWechatUnionId(String unionId) {
        if (!StringUtils.hasText(unionId)) {
            return null;
        }
        return identityMapper.selectOne(new LambdaQueryWrapper<UserAuthIdentity>()
                .in(UserAuthIdentity::getProvider, WECHAT_PROVIDERS)
                .eq(UserAuthIdentity::getUnionId, unionId)
                .last("limit 1"));
    }

    private boolean isWechatProvider(AuthProvider provider) {
        return provider == AuthProvider.WECHAT_MINI || provider == AuthProvider.WECHAT_APP;
    }

    @Override
    public void releaseAllIdentitiesForDeletedUser(String userId) {
        List<UserAuthIdentity> identities = identityIncludeDeletedJdbc.findByUserId(userId);
        if (identities.isEmpty()) {
            return;
        }
        LocalDateTime now = DateTimeUtil.nowUtc();
        for (UserAuthIdentity identity : identities) {
            tombstoneIdentity(identity, now);
        }
        log.info("Auth identities released for deleted user userId={} count={}", userId, identities.size());
    }

    private void tombstoneIdentity(UserAuthIdentity identity) {
        tombstoneIdentity(identity, DateTimeUtil.nowUtc());
    }

    private void tombstoneIdentity(UserAuthIdentity identity, LocalDateTime now) {
        String tombstoneProviderUserId = "deleted:" + identity.getUserId() + ":" + identity.getId();
        identityMapper.update(null, new UpdateWrapper<UserAuthIdentity>()
                .eq("id", identity.getId())
                .set("provider_user_id", tombstoneProviderUserId)
                .set("union_id", null)
                .set("deleted_at", now)
                .set("updated_at", now));
    }

    private void updateIdentityUnionIdIfNeeded(UserAuthIdentity identity, String unionId) {
        if (!StringUtils.hasText(unionId) || unionId.equals(identity.getUnionId())) {
            return;
        }
        identity.setUnionId(unionId);
        identityMapper.update(null, new UpdateWrapper<UserAuthIdentity>()
                .eq("id", identity.getId())
                .set("union_id", unionId));
    }

    private void touchLastLogin(User user, LoginType loginType) {
        LocalDateTime now = DateTimeUtil.nowUtc();
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

    private String resolveNickname(String nickname) {
        return StringUtils.hasText(nickname) ? nickname.trim() : DEFAULT_NICKNAME;
    }
}
