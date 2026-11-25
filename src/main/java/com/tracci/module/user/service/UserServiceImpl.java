package com.tracci.module.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.tracci.common.context.LoginUserContext;
import com.tracci.common.exception.BizException;
import com.tracci.common.exception.ForbiddenException;
import com.tracci.common.exception.NotFoundException;
import com.tracci.common.response.ErrorCode;
import com.tracci.common.support.UserAccountStatus;
import com.tracci.common.util.DateTimeUtil;
import com.tracci.integration.cos.FileUrlResolver;
import com.tracci.module.auth.entity.UserAuthIdentity;
import com.tracci.module.auth.enums.AuthProvider;
import com.tracci.module.auth.repository.UserAuthIdentityMapper;
import com.tracci.module.auth.service.AuthIdentityService;
import com.tracci.module.auth.support.AuthRedisKeys;
import com.tracci.module.file.entity.File;
import com.tracci.module.file.repository.FileMapper;
import com.tracci.module.trip.repository.TripMapper;
import com.tracci.module.user.dto.request.DeleteAccountReq;
import com.tracci.module.user.dto.request.UserUpdateReq;
import com.tracci.module.user.dto.response.UserProfileResp;
import com.tracci.module.user.dto.response.UserSecurityResp;
import com.tracci.module.user.entity.User;
import com.tracci.module.user.repository.UserIncludeDeletedJdbc;
import com.tracci.module.user.repository.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserServiceImpl implements UserService {

    private static final String DELETE_CONFIRM_TEXT = "DELETE";
    private static final String DELETED_NICKNAME = "已注销用户";

    private final UserMapper userMapper;
    private final UserIncludeDeletedJdbc userIncludeDeletedJdbc;
    private final FileMapper fileMapper;
    private final FileUrlResolver fileUrlResolver;
    private final AuthIdentityService authIdentityService;
    private final UserAuthIdentityMapper identityMapper;
    private final StringRedisTemplate redis;
    private final PasswordEncoder passwordEncoder;
    private final TripMapper tripMapper;

    @Override
    @Transactional(readOnly = true)
    public UserProfileResp getMe() {
        String userId = LoginUserContext.requireUserId();
        User user = userIncludeDeletedJdbc.findById(userId);
        if (user == null) {
            throw new NotFoundException(ErrorCode.USER_NOT_FOUND);
        }
        UserAccountStatus.requireActive(user);
        return toProfile(user);
    }

    @Override
    @Transactional(readOnly = true)
    public UserSecurityResp getSecurity() {
        String userId = LoginUserContext.requireUserId();
        User user = userIncludeDeletedJdbc.findById(userId);
        if (user == null) {
            throw new NotFoundException(ErrorCode.USER_NOT_FOUND);
        }
        UserAccountStatus.requireActive(user);

        // 查询已绑定的 identities
        List<UserAuthIdentity> identities = identityMapper.selectList(
                new LambdaQueryWrapper<UserAuthIdentity>()
                        .eq(UserAuthIdentity::getUserId, userId));

        Map<String, Boolean> bindings = new HashMap<>();
        bindings.put("email", identities.stream().anyMatch(i -> AuthProvider.EMAIL.name().equals(i.getProvider())));
        bindings.put("phone", identities.stream().anyMatch(i -> AuthProvider.PHONE.name().equals(i.getProvider())));
        bindings.put("wechat", identities.stream().anyMatch(i ->
                AuthProvider.WECHAT_MINI.name().equals(i.getProvider())
                        || AuthProvider.WECHAT_APP.name().equals(i.getProvider())));
        bindings.put("apple", identities.stream().anyMatch(i -> AuthProvider.APPLE.name().equals(i.getProvider())));

        return new UserSecurityResp(
                StringUtils.hasText(user.getEmail()) ? user.getEmail() : null,
                StringUtils.hasText(user.getPhone()) ? user.getPhone() : null,
                Boolean.TRUE.equals(user.getPhoneVerified()),
                StringUtils.hasText(user.getPasswordHash()),
                bindings
        );
    }

    @Override
    @Transactional
    public UserProfileResp updateMe(UserUpdateReq req) {
        boolean touchNickname = req.nickname() != null;
        boolean touchAvatar = req.avatarFileId() != null;
        boolean touchSignature = req.signature() != null;
        boolean touchGender = req.gender() != null;
        boolean touchBirthDate = req.birthDate() != null;
        if (!touchNickname && !touchAvatar && !touchSignature && !touchGender && !touchBirthDate) {
            throw new BizException(ErrorCode.AUTH_PARAM_INVALID, "请至少提供一个要更新的字段");
        }

        String userId = LoginUserContext.requireUserId();
        User user = userIncludeDeletedJdbc.findById(userId);
        if (user == null) {
            throw new NotFoundException(ErrorCode.USER_NOT_FOUND);
        }
        UserAccountStatus.requireActive(user);

        UpdateWrapper<User> update = new UpdateWrapper<User>()
                .eq("id", userId);

        if (touchNickname) {
            String nickname = req.nickname().trim();
            if (!StringUtils.hasText(nickname)) {
                throw new BizException(ErrorCode.AUTH_PARAM_INVALID, "昵称不能为空");
            }
            user.setNickname(nickname);
            update.set("nickname", nickname);
        }

        if (touchAvatar) {
            String avatarFileId = req.avatarFileId().trim();
            if (!StringUtils.hasText(avatarFileId)) {
                user.setAvatarFileId(null);
                user.setAvatarObjectKey(null);
                update.set("avatar_file_id", null);
                update.set("avatar_object_key", null);
            } else {
                File avatarFile = getValidAvatarFile(avatarFileId, userId);
                user.setAvatarFileId(avatarFile.getId());
                user.setAvatarObjectKey(avatarFile.getObjectKey());
                update.set("avatar_file_id", avatarFile.getId());
                update.set("avatar_object_key", avatarFile.getObjectKey());
            }
        }

        if (touchSignature) {
            String signature = cleanOrNull(req.signature());
            user.setSignature(signature);
            update.set("signature", signature);
        }

        if (touchGender) {
            user.setGender(req.gender().name());
            update.set("gender", req.gender().name());
        }

        if (touchBirthDate) {
            String birthDate = cleanOrNull(req.birthDate());
            user.setBirthDate(birthDate);
            update.set("birth_date", birthDate);
        }

        userMapper.update(null, update);
        return toProfile(user);
    }

    @Override
    @Transactional
    public void deleteAccount(DeleteAccountReq req) {
        String userId = LoginUserContext.requireUserId();
        User user = userIncludeDeletedJdbc.findById(userId);
        if (user == null) {
            throw new NotFoundException(ErrorCode.USER_NOT_FOUND);
        }
        if (UserAccountStatus.isDeleted(user)) {
            return;
        }
        UserAccountStatus.requireActive(user);

        if (!DELETE_CONFIRM_TEXT.equals(req.confirmText())) {
            throw new BizException(ErrorCode.AUTH_PARAM_INVALID, "confirmText 必须为 DELETE");
        }

        if (StringUtils.hasText(user.getPasswordHash())) {
            if (!StringUtils.hasText(req.password())
                    || !passwordEncoder.matches(req.password(), user.getPasswordHash())) {
                throw new BizException(ErrorCode.EMAIL_OR_PASSWORD_INVALID, "密码错误");
            }
        }

        // 先检查是否拥有含其他有效成员的行程，避免对大量行程行加锁。
        if (tripMapper.countOwnedTripsWithOtherEffectiveMembers(userId) > 0) {
            throw new BizException(ErrorCode.ACCOUNT_OWNS_SHARED_TRIPS);
        }
        // 通过检查后，对用户拥有的行程加行锁，防止删除账号期间其他事务修改行程成员。
        tripMapper.lockOwnedTripsForUpdate(userId);

        LocalDateTime now = DateTimeUtil.nowUtc();
        long epoch = now.toInstant(ZoneOffset.UTC).toEpochMilli();
        String tombstoneEmail = buildTombstoneEmail(userId, epoch);

        authIdentityService.releaseAllIdentitiesForDeletedUser(userId);
        redis.delete(AuthRedisKeys.refreshToken(userId));

        int updated = userMapper.update(null, new UpdateWrapper<User>()
                .eq("id", userId)
                .eq("status", UserAccountStatus.ACTIVE)
                .isNull("deleted_at")
                .set("status", UserAccountStatus.DELETED)
                .set("deleted_at", now)
                .set("deleted_reason", truncateReason(req.reason()))
                .set("anonymized_at", now)
                .set("nickname", DELETED_NICKNAME)
                .set("phone", null)
                .set("password_hash", null)
                .set("avatar_file_id", null)
                .set("avatar_object_key", null)
                .set("signature", null)
                .set("gender", null)
                .set("birth_date", null)
                .set("email", tombstoneEmail)
                .set("updated_at", now));
        if (updated != 1) {
            User latest = userIncludeDeletedJdbc.findById(userId);
            if (UserAccountStatus.isDeleted(latest)) {
                return;
            }
            UserAccountStatus.requireActive(latest);
            throw new BizException(ErrorCode.INTERNAL_ERROR, "账号注销未完成，请重试");
        }

        log.info("User account deleted userId={} reasonPresent={}",
                userId, StringUtils.hasText(req.reason()));
    }

    private static String buildTombstoneEmail(String userId, long epoch) {
        String suffix = userId.replace("-", "");
        if (suffix.length() > 8) {
            suffix = suffix.substring(0, 8);
        }
        return "del_" + suffix + "_" + epoch + "@deleted.tracci.local";
    }

    private static String truncateReason(String reason) {
        if (!StringUtils.hasText(reason)) {
            return null;
        }
        String trimmed = reason.trim();
        return trimmed.length() > 200 ? trimmed.substring(0, 200) : trimmed;
    }

    private UserProfileResp toProfile(User user) {
        String createdAt = null;
        if (user.getCreatedAt() != null) {
            createdAt = DateTimeUtil.toInstantString(user.getCreatedAt());
        }
        return new UserProfileResp(
                user.getId(),
                StringUtils.hasText(user.getEmail()) ? user.getEmail() : null,
                StringUtils.hasText(user.getPhone()) ? user.getPhone() : null,
                Boolean.TRUE.equals(user.getPhoneVerified()),
                StringUtils.hasText(user.getNickname()) ? user.getNickname() : null,
                StringUtils.hasText(user.getAvatarFileId()) ? user.getAvatarFileId() : null,
                resolveAvatarUrl(user),
                StringUtils.hasText(user.getLoginType()) ? user.getLoginType() : null,
                StringUtils.hasText(user.getSignature()) ? user.getSignature() : null,
                StringUtils.hasText(user.getGender()) ? user.getGender() : null,
                StringUtils.hasText(user.getBirthDate()) ? user.getBirthDate() : null,
                createdAt
        );
    }

    private File getValidAvatarFile(String avatarFileId, String userId) {
        File file = fileMapper.selectById(avatarFileId);
        if (file == null || "DELETED".equals(file.getStatus())) {
            throw new NotFoundException(ErrorCode.FILE_NOT_FOUND);
        }
        if (!userId.equals(file.getUserId())) {
            throw new ForbiddenException();
        }
        if (!"UPLOADED".equals(file.getStatus())) {
            throw new BizException(ErrorCode.FILE_NOT_UPLOADED, "头像文件尚未上传完成");
        }
        if (!"AVATAR".equals(file.getBusinessType())) {
            throw new BizException(ErrorCode.AUTH_PARAM_INVALID, "头像文件类型必须为 AVATAR");
        }
        return file;
    }

    private String resolveAvatarUrl(User user) {
        if (!StringUtils.hasText(user.getAvatarFileId())) {
            return null;
        }
        File file = fileMapper.selectById(user.getAvatarFileId());
        if (file == null || "DELETED".equals(file.getStatus())) {
            return null;
        }
        if (!user.getId().equals(file.getUserId())) {
            return null;
        }
        return fileUrlResolver.resolve(file);
    }

    private static String cleanOrNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return StringUtils.hasText(trimmed) ? trimmed : null;
    }
}
