package com.tracci.module.auth.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.tracci.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 用户身份绑定表 user_auth_identities。
 * <p>
 * 一个 user 可绑定多个 identity（EMAIL、WECHAT_MINI 等），
 * 同一 provider + provider_user_id 只能绑定一个有效 userId。
 */
@Getter
@Setter
@TableName("user_auth_identities")
public class UserAuthIdentity extends BaseEntity {

    @TableField("user_id")
    private String userId;

    /** 身份提供方（EMAIL / PHONE / WECHAT_MINI / WECHAT_APP / APPLE） */
    @TableField("provider")
    private String provider;

    /**
     * 提供方用户标识：
     * - EMAIL: 标准化邮箱小写
     * - PHONE: E.164 手机号
     * - WECHAT_MINI / WECHAT_APP: openid
     * - APPLE: Apple User ID
     */
    @TableField("provider_user_id")
    private String providerUserId;

    /** 微信 unionid，用于跨端归并 */
    @TableField("union_id")
    private String unionId;

    /** 冗余存储邮箱（EMAIL provider 时填写） */
    @TableField("email")
    private String email;

    /** 冗余存储手机号（PHONE provider 时填写） */
    @TableField("phone")
    private String phone;

    /** active / disabled / deleted */
    @TableField("status")
    private String status;

    /** 绑定时间 */
    @TableField("bound_at")
    private LocalDateTime boundAt;
}
