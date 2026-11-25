package com.tracci.module.feedback.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.tracci.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@TableName("feedbacks")
public class Feedback extends BaseEntity {

    @TableField("user_id")
    private String userId;

    @TableField("type")
    private String type;

    @TableField("title")
    private String title;

    @TableField("content")
    private String content;

    @TableField("contact_type")
    private String contactType;

    @TableField("contact")
    private String contact;

    @TableField("status")
    private String status;

    @TableField("app_platform")
    private String appPlatform;

    @TableField("app_version")
    private String appVersion;

    @TableField("system_info")
    private String systemInfo;

    @TableField("handled_at")
    private LocalDateTime handledAt;

    @TableField("handler_note")
    private String handlerNote;
}
