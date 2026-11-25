package com.tracci.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Admin access configuration.
 *
 * <p>Bind target: {@code tracci.admin}
 *
 * <p>v1 simple admin check: current user's userId must appear in {@link #userIds}
 * or match one of {@link #adminEmails}.  Later replace with proper RBAC.
 *
 * <p>On startup, {@link com.tracci.config.AdminInitializer} will auto-create a user for each
 * email that does not yet exist in the {@code users} table, using
 * {@link #defaultPassword} as the initial password.  The default password is
 * only used for first-time setup; the admin should change it immediately.
 */
@ConfigurationProperties(prefix = "tracci.admin")
public class AdminProperties {

    /**
     * User IDs that are treated as administrators.
     * Populate with real user IDs from the {@code users} table.
     *
     * <p>Stored as a {@link Set} for O(1) {@code contains()} lookups.
     */
    private Set<String> userIds = new LinkedHashSet<>();

    /**
     * Email addresses that bypass the userId check.
     * Useful for test accounts whose userId is a UUID but whose email is known.
     *
     * <p>On startup, {@link AdminInitializer} will auto-create a user for each
     * email that does not yet exist in the {@code users} table.
     */
    private Set<String> adminEmails = new LinkedHashSet<>();

    /**
     * Default password for auto-created admin users.
     * Must be set in production; if {@code null} or blank, a random password is generated and
     * logged at WARN level.
     */
    private String defaultPassword = null;

    public Set<String> getUserIds() {
        return userIds;
    }

    public void setUserIds(Set<String> userIds) {
        this.userIds = userIds;
    }

    public Set<String> getAdminEmails() {
        return adminEmails;
    }

    public void setAdminEmails(Set<String> adminEmails) {
        this.adminEmails = adminEmails;
    }

    public String getDefaultPassword() {
        return defaultPassword;
    }

    public void setDefaultPassword(String defaultPassword) {
        this.defaultPassword = defaultPassword;
    }
}
