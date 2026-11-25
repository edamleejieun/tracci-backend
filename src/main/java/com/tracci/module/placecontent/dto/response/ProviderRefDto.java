package com.tracci.module.placecontent.dto.response;

import java.time.LocalDateTime;

/**
 * 地点 Provider 身份引用（管理端展示用）。
 * AMAP 引用为服务端核验后写入；APPLE/MANUAL 引用代表该 Place 的创建来源证据。
 */
public record ProviderRefDto(
        String provider,
        String providerPlaceId,
        Boolean isPrimary,
        LocalDateTime lastCheckedAt
) {
}
