package com.tracci.module.placecontent.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.tracci.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 地点媒体资产。Place 与合法图片资产或 Provider remote reference 的审核关系。
 */
@Getter
@Setter
@TableName("place_media_assets")
public class PlaceMediaAsset extends BaseEntity {

    @TableField("place_id")
    private String placeId;

    @TableField("file_id")
    private String fileId;

    @TableField("source_type")
    private String sourceType;

    @TableField("source_reference")
    private String sourceReference;

    @TableField("license_type")
    private String licenseType;

    @TableField("attribution")
    private String attribution;

    @TableField("alt_text")
    private String altText;

    @TableField("width")
    private Integer width;

    @TableField("height")
    private Integer height;

    @TableField("sort_order")
    private Integer sortOrder;

    @TableField("status")
    private String status;

    @TableField("reviewed_by")
    private String reviewedBy;

    @TableField("reviewed_at")
    private java.time.LocalDateTime reviewedAt;

    @TableField("rights_expires_at")
    private java.time.LocalDateTime rightsExpiresAt;
}
