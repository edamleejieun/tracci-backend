package com.tracci.module.auth.service;

import com.tracci.module.user.entity.User;

public record AuthIdentityLoginResult(User user, boolean isNewUser) {
}
