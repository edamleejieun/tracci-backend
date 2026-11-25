package com.tracci.module.feedback.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.tracci.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("feedback_attachments")
public class FeedbackAttachment extends BaseEntity {

    @TableField("feedback_id")
    private String feedbackId;

    @TableField("user_id")
    private String userId;

    @TableField("file_id")
    private String fileId;

    @TableField("sort_order")
    private Integer sortOrder;
}
