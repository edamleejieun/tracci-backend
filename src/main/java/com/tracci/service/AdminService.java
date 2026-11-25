package com.tracci.service;

import com.tracci.config.AdminProperties;
import com.tracci.module.user.entity.User;
import com.tracci.module.user.repository.UserMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * v1 simple admin check.
 *
 * <p>An user is considered admin when:
 * <ul>
 *   <li>their {@code userId} appears in {@link AdminProperties#getUserIds()}, OR</li>
 *   <li>their email (looked up from the {@code users} table) appears in
 *       {@link AdminProperties#getAdminEmails()}</li>
 * </ul>
 *
 * <p>Later replace with proper RBAC (roles / permissions tables).
 */
@Service
@RequiredArgsConstructor
public class AdminService {

    private final AdminProperties adminProperties;
    private final UserMapper userMapper;

    /** Cached lowercase email set, populated at startup and refreshable. */
    private volatile Set<String> adminEmailSet = Set.of();

    @PostConstruct
    public void init() {
        refreshAdminEmailCache();
    }

    /** Refresh the cached admin email set from {@link AdminProperties}. */
    public void refreshAdminEmailCache() {
        Set<String> emails = adminProperties.getAdminEmails();
        if (emails == null || emails.isEmpty()) {
            adminEmailSet = Set.of();
            return;
        }
        adminEmailSet = emails.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .map(String::toLowerCase)
                .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * Returns {@code true} if the current logged-in user is an admin.
     *
     * <p>Must be called from an already-authenticated request context.
     */
    public boolean isCurrentUserAdmin() {
        String userId = com.tracci.common.context.LoginUserContext.getUserIdOrNull();
        if (userId == null || userId.isBlank()) {
            return false;
        }

        Set<String> adminUserIds = adminProperties.getUserIds();
        if (adminUserIds != null && adminUserIds.contains(userId)) {
            return true;
        }

        // Fallback: check cached email whitelist
        if (adminEmailSet.isEmpty()) {
            return false;
        }

        User user = userMapper.selectById(userId);
        if (user == null || user.getEmail() == null) {
            return false;
        }

        return adminEmailSet.contains(user.getEmail().toLowerCase());
    }
}
