package com.tracci.module.savedplace.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.tracci.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("saved_places")
public class SavedPlace extends BaseEntity {

    @TableField("user_id")
    private String userId;

    @TableField("place_id")
    private String placeId;

    @TableField("note")
    private String note;

    @TableField("source")
    private String source;
}
