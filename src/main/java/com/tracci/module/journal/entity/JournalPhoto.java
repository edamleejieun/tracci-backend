package com.tracci.module.journal.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.tracci.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("journal_photos")
public class JournalPhoto extends BaseEntity {

    @TableField("journal_entry_id")
    private String journalEntryId;

    @TableField("trip_id")
    private String tripId;

    @TableField("user_id")
    private String userId;

    @TableField("file_id")
    private String fileId;

    @TableField("sort_order")
    private Integer sortOrder;

    @TableField("is_featured")
    private Boolean isFeatured;
}
