package com.tracci.module.user.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.tracci.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 用户表 users，与 database-design 对齐
 */
@Getter
@Setter
@TableName("users")
public class User extends BaseEntity {

    /** 邮箱（统一小写存储） */
    @TableField("email")
    private String email;

    /** 手机号（E.164 格式，如 +8613812345678） */
    @TableField("phone")
    private String phone;

    /** 手机号是否已验证 */
    @TableField("phone_verified")
    private Boolean phoneVerified;

    /** 手机号验证时间 */
    @TableField("phone_verified_at")
    private LocalDateTime phoneVerifiedAt;

    @TableField("password_hash")
    private String passwordHash;

    @TableField("nickname")
    private String nickname;

    @TableField("avatar_file_id")
    private String avatarFileId;

    @TableField("avatar_object_key")
    private String avatarObjectKey;

    /** 登录方式（EMAIL / SMS / WECHAT_MINI / WECHAT_IOS / APPLE） */
    @TableField("login_type")
    private String loginType;

    @TableField("last_login_at")
    private LocalDateTime lastLoginAt;

    /** active / disabled / deleted */
    @TableField("status")
    private String status;

    @TableField("deleted_reason")
    private String deletedReason;

    @TableField("anonymized_at")
    private LocalDateTime anonymizedAt;

    /** 旅行宣言，最大 100 字符 */
    @TableField("signature")
    private String signature;

    /** 性别：MALE / FEMALE / UNDISCLOSED */
    @TableField("gender")
    private String gender;

    /** 出生年月，格式 YYYY-MM */
    @TableField("birth_date")
    private String birthDate;
}
