package com.tracci.module.file.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.tracci.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("files")
public class File extends BaseEntity {

    @TableField("user_id")
    private String userId;

    @TableField("business_type")
    private String businessType;

    @TableField("business_id")
    private String businessId;

    @TableField("original_file_name")
    private String originalFileName;

    @TableField("content_type")
    private String contentType;

    @TableField("size")
    private Long size;

    @TableField("object_key")
    private String objectKey;

    @TableField("bucket")
    private String bucket;

    @TableField("region")
    private String region;

    @TableField("url")
    private String url;

    @TableField("width")
    private Integer width;

    @TableField("height")
    private Integer height;

    @TableField("etag")
    private String etag;

    @TableField("status")
    private String status;
}
