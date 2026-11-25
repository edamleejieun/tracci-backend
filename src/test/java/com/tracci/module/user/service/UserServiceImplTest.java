package com.tracci.module.user.service;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.tracci.common.context.LoginUserContext;
import com.tracci.common.exception.BizException;
import com.tracci.common.exception.ForbiddenException;
import com.tracci.integration.cos.FileUrlResolver;
import com.tracci.module.auth.enums.LoginType;
import com.tracci.module.file.entity.File;
import com.tracci.module.file.repository.FileMapper;
import com.tracci.module.user.dto.request.UserUpdateReq;
import com.tracci.module.user.dto.response.UserProfileResp;
import com.tracci.module.user.entity.User;
import com.tracci.module.user.repository.UserIncludeDeletedJdbc;
import com.tracci.module.user.repository.UserMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserMapper userMapper;
    @Mock
    private UserIncludeDeletedJdbc userIncludeDeletedJdbc;
    @Mock
    private FileMapper fileMapper;
    @Mock
    private FileUrlResolver fileUrlResolver;
    @Mock
    private com.tracci.module.auth.service.AuthIdentityService authIdentityService;
    @Mock
    private org.springframework.data.redis.core.StringRedisTemplate redis;
    @Mock
    private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserServiceImpl userService;

    @Test
    void updateMe_rejectsEmptyPayload() {
        LoginUserContext.setUserId("u-1");
        try {
            assertThrows(BizException.class, () -> userService.updateMe(new UserUpdateReq(null, null, null, null, null)));
            verify(userMapper, never()).updateById(any(User.class));
        } finally {
            LoginUserContext.clear();
        }
    }

    @Test
    void updateMe_updatesNickname() {
        User stored = new User();
        stored.setId("u-1");
        stored.setPhone("+8613800138000");
        stored.setNickname("旧");
        stored.setStatus("active");
        stored.setCreatedAt(LocalDateTime.of(2026, 1, 1, 0, 0));

        when(userIncludeDeletedJdbc.findById("u-1")).thenReturn(stored);

        LoginUserContext.setUserId("u-1");
        try {
            UserProfileResp resp = userService.updateMe(new UserUpdateReq("新昵称", null, null, null, null));
            assertEquals("新昵称", resp.nickname());
            verify(userMapper).update(isNull(), any(UpdateWrapper.class));
        } finally {
            LoginUserContext.clear();
        }
    }

    @Test
    void getMe_returnsLoginType() {
        User stored = activeUser();
        stored.setLoginType(LoginType.EMAIL.name());
        when(userIncludeDeletedJdbc.findById("u-1")).thenReturn(stored);

        LoginUserContext.setUserId("u-1");
        try {
            UserProfileResp resp = userService.getMe();
            assertEquals(LoginType.EMAIL.name(), resp.loginType());
        } finally {
            LoginUserContext.clear();
        }
    }

    @Test
    void updateMe_updatesAvatarFile() {
        User stored = activeUser();
        File avatar = avatarFile("file-1", "u-1");
        when(userIncludeDeletedJdbc.findById("u-1")).thenReturn(stored);
        when(fileMapper.selectById("file-1")).thenReturn(avatar);
        when(fileUrlResolver.resolve(avatar)).thenReturn("https://signed.example.com/avatar.jpg");

        LoginUserContext.setUserId("u-1");
        try {
            UserProfileResp resp = userService.updateMe(new UserUpdateReq(null, "file-1", null, null, null));

            assertEquals("file-1", resp.avatarFileId());
            assertEquals("https://signed.example.com/avatar.jpg", resp.avatarUrl());
            verify(userMapper).update(isNull(), any(UpdateWrapper.class));
        } finally {
            LoginUserContext.clear();
        }
    }

    @Test
    void updateMe_rejectsOtherUserAvatarFile() {
        User stored = activeUser();
        File avatar = avatarFile("file-1", "u-2");
        when(userIncludeDeletedJdbc.findById("u-1")).thenReturn(stored);
        when(fileMapper.selectById("file-1")).thenReturn(avatar);

        LoginUserContext.setUserId("u-1");
        try {
            assertThrows(ForbiddenException.class,
                    () -> userService.updateMe(new UserUpdateReq(null, "file-1", null, null, null)));
        } finally {
            LoginUserContext.clear();
        }
    }

    @Test
    void updateMe_clearsAvatarFile() {
        User stored = activeUser();
        stored.setAvatarFileId("file-1");
        stored.setAvatarObjectKey("users/u-1/avatar/2026/05/a.jpg");
        when(userIncludeDeletedJdbc.findById("u-1")).thenReturn(stored);

        LoginUserContext.setUserId("u-1");
        try {
            UserProfileResp resp = userService.updateMe(new UserUpdateReq(null, "  ", null, null, null));
            assertNull(resp.avatarFileId());
            assertNull(resp.avatarUrl());
        } finally {
            LoginUserContext.clear();
        }
    }

    @Test
    void getMe_rejectsDisabledUser() {
        User stored = new User();
        stored.setId("u-1");
        stored.setStatus("disabled");
        when(userIncludeDeletedJdbc.findById("u-1")).thenReturn(stored);

        LoginUserContext.setUserId("u-1");
        try {
            assertThrows(BizException.class, () -> userService.getMe());
        } finally {
            LoginUserContext.clear();
        }
    }

    private User activeUser() {
        User user = new User();
        user.setId("u-1");
        user.setPhone("+8613800138000");
        user.setNickname("旅行者");
        user.setStatus("active");
        user.setCreatedAt(LocalDateTime.of(2026, 1, 1, 0, 0));
        return user;
    }

    private File avatarFile(String id, String userId) {
        File file = new File();
        file.setId(id);
        file.setUserId(userId);
        file.setBusinessType("AVATAR");
        file.setObjectKey("users/" + userId + "/avatar/2026/05/a.jpg");
        file.setStatus("UPLOADED");
        return file;
    }
}
