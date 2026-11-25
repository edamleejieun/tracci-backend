package com.tracci.module.user.dto.request;

import com.tracci.module.user.enums.Gender;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 当前用户资料局部更新（有字段非 null 时才更新该字段）
 */
public record UserUpdateReq(
        @Size(max = 50, message = "昵称长度不能超过 50")
        String nickname,

        @Size(max = 36, message = "头像文件 ID 长度不能超过 36")
        String avatarFileId,

        @Size(max = 100, message = "旅行宣言长度不能超过 100")
        String signature,

        Gender gender,

        @Pattern(regexp = "^\\d{4}-(0[1-9]|1[0-2])$", message = "出生年月格式必须为 YYYY-MM")
        String birthDate
) {
}
