package com.tracci.module.placecontent.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.tracci.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 内容版本的事实来源。绑定某个内容版本的一条"字段级事实来源"。
 */
@Getter
@Setter
@TableName("place_content_sources")
public class PlaceContentSource extends BaseEntity {

    @TableField("content_version_id")
    private String contentVersionId;

    @TableField("fact_key")
    private String factKey;

    @TableField("fact_value")
    private String factValue;

    @TableField("source_type")
    private String sourceType;

    @TableField("source_title")
    private String sourceTitle;

    @TableField("source_url")
    private String sourceUrl;

    @TableField("retrieved_at")
    private java.time.LocalDateTime retrievedAt;

    @TableField("expires_at")
    private java.time.LocalDateTime expiresAt;

    @TableField("verification_status")
    private String verificationStatus;

    @TableField("verified_by")
    private String verifiedBy;

    @TableField("verified_at")
    private java.time.LocalDateTime verifiedAt;

    @TableField("source_hash")
    private String sourceHash;

    @TableField("note")
    private String note;
}
