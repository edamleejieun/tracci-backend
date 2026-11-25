package com.tracci.module.placecontent.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/**
 * 内容版本关联的一条字段级事实来源。
 */
public record SourceFactRequest(
        @NotBlank(message = "factKey 不能为空")
        @Size(max = 80, message = "factKey 长度不能超过 80")
        String factKey,

        @NotBlank(message = "factValue 不能为空")
        String factValue,

        @NotBlank(message = "sourceType 不能为空")
        @Size(max = 40, message = "sourceType 长度不能超过 40")
        String sourceType,

        @Size(max = 300, message = "sourceTitle 长度不能超过 300")
        String sourceTitle,

        String sourceUrl,

        /** 取得时间；为空时服务端填入当前 UTC */
        LocalDateTime retrievedAt,

        LocalDateTime expiresAt,

        /** COLLECTED / VERIFIED / REJECTED / EXPIRED；默认 COLLECTED */
        @Size(max = 20, message = "verificationStatus 不合法")
        String verificationStatus,

        @Size(max = 500, message = "note 长度不能超过 500")
        String note
) {
}
