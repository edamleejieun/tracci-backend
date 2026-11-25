package com.tracci.config;

import com.tracci.module.auth.enums.LoginType;
import com.tracci.module.auth.service.AuthIdentityService;
import com.tracci.module.user.entity.User;
import com.tracci.module.user.repository.UserMapper;
import com.tracci.service.AdminService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Set;

/**
 * On application startup, ensures that every email listed in {@link AdminProperties#getAdminEmails()}
 * has a corresponding {@link User} with admin privileges.
 *
 * <p>If a user does not exist, one is auto-created with a default password.
 * The default password comes from {@link AdminProperties#getDefaultPassword()};
 * if that is empty, a random password is generated and logged at WARN level.
 *
 * <p>This is a v1 convenience for dev/test and first-time prod deployments.
 * Replace with proper user management / RBAC in later phases.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "tracci.admin", name = "auto-create-enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class AdminInitializer implements CommandLineRunner {

    private static final String DEFAULT_NICKNAME = "管理员";
    private static final String DEFAULT_STATUS = "active";

    private final AdminProperties adminProperties;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final AuthIdentityService authIdentityService;
    private final AdminService adminService;

    @Override
    public void run(String... args) {
        Set<String> adminEmails = adminProperties.getAdminEmails();
        if (adminEmails == null || adminEmails.isEmpty()) {
            return;
        }

        String defaultPassword = adminProperties.getDefaultPassword();

        for (String rawEmail : adminEmails) {
            if (rawEmail == null || rawEmail.isBlank()) {
                continue;
            }
            String email = rawEmail.toLowerCase().trim();

            User existing = userMapper.selectOne(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<User>()
                            .eq(User::getEmail, email)
                            .last("LIMIT 1"));

            if (existing != null) {
                log.info("Admin user already exists: email={} userId={}", email, existing.getId());
                continue;
            }

            String password = defaultPassword != null && !defaultPassword.isBlank()
                    ? defaultPassword
                    : generateRandomPassword(12);

            User user = new User();
            user.setEmail(email);
            user.setPasswordHash(passwordEncoder.encode(password));
            user.setNickname(DEFAULT_NICKNAME);
            user.setStatus(DEFAULT_STATUS);
            user.setLoginType(LoginType.EMAIL.name());
            user.setPhoneVerified(false);
            user.setLastLoginAt(com.tracci.common.util.DateTimeUtil.nowUtc());

            try {
                userMapper.insert(user);
                authIdentityService.createEmailIdentity(user.getId(), email);
                log.warn("==============================================================");
                log.warn("Auto-created admin user: email={} userId={}", email, user.getId());
                if (defaultPassword != null && !defaultPassword.isBlank()) {
                    log.warn("Admin user created using configured default password (CHANGE THIS IMMEDIATELY)");
                } else {
                    log.warn("Randomly generated admin password (length={}) (CHANGE THIS IMMEDIATELY)", password.length());
                }
                log.warn("==============================================================");
            } catch (Exception ex) {
                log.error("Failed to auto-create admin user email={}: {}", email, ex.getMessage(), ex);
            }
        }

        adminService.refreshAdminEmailCache();
        log.info("Admin email cache refreshed with {} email(s)", adminProperties.getAdminEmails().size());
    }

    private static String generateRandomPassword(int length) {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%";
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }
}
