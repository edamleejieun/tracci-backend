package com.tracci.module.auth.service;

import com.tracci.module.auth.enums.AuthProvider;
import com.tracci.module.auth.enums.LoginType;

public interface AuthIdentityService {

    /**
     * 按 identity 查找或创建用户（微信登录等场景）。
     * <p>
     * 查找顺序：
     * 1. 按 (provider, provider_user_id) 精确匹配
     * 2. 若 unionId 存在，按 unionId 跨 WECHAT_MINI/WECHAT_APP 归并
     * 3. 都找不到则创建新用户和 identity
     */
    AuthIdentityLoginResult findOrCreateUserByIdentity(
            AuthProvider provider,
            String providerUserId,
            String unionId,
            LoginType loginType);

    AuthIdentityLoginResult findOrCreateUserByIdentity(
            AuthProvider provider,
            String providerUserId,
            String unionId,
            LoginType loginType,
            String nickname);

    /**
     * 为邮箱注册用户创建 EMAIL identity
     */
    void createEmailIdentity(String userId, String email);

    /**
     * 查找指定 provider 的 identity（不含已删除）
     */
    com.tracci.module.auth.entity.UserAuthIdentity findActiveIdentityByEmail(String email);

    /**
     * 注销账号时释放所有身份绑定（墓碑化 provider_user_id、清空 union_id）
     */
    void releaseAllIdentitiesForDeletedUser(String userId);
}
