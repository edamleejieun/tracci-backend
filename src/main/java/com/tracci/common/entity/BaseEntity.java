package com.tracci.common.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 所有业务实体的基础类
 * <p>
 * 包含：id（UUID）、createdAt、updatedAt、deletedAt（逻辑删除）
 * 子类使用 @TableName 标注对应的数据库表名
 */
@Getter
@Setter
public abstract class BaseEntity {

    /** 主键，UUID 自动生成 */
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    /** 创建时间，INSERT 时自动填充 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 更新时间，INSERT 和 UPDATE 时自动填充 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /**
     * 逻辑删除字段：null 表示未删除，有值表示已删除
     * MyBatis-Plus 自动处理，查询时默认过滤 deleted_at IS NOT NULL 的记录
     */
    @TableLogic(value = "null", delval = "now()")
    private LocalDateTime deletedAt;
}
