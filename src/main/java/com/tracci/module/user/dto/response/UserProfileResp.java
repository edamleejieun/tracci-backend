package com.tracci.module.user.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 当前用户对外展示信息（未设置的字段不输出）
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record UserProfileResp(
        String id,
        String email,
        String phone,
        boolean phoneVerified,
        String nickname,
        String avatarFileId,
        String avatarUrl,
        String loginType,
        String signature,
        String gender,
        String birthDate,
        String createdAt
) {
}
