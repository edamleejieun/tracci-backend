package com.tracci.module.placecontent.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tracci.module.placecontent.entity.PlaceContentSource;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 地点内容来源数据访问。
 */
@Mapper
public interface PlaceContentSourceMapper extends BaseMapper<PlaceContentSource> {

    /**
     * 逻辑删除某版本下全部来源（用于草稿替换 sources）。
     */
    @Update("""
            UPDATE place_content_sources
            SET deleted_at = #{deletedAt}, updated_at = #{deletedAt}
            WHERE content_version_id = #{contentVersionId}
              AND deleted_at IS NULL
            """)
    int softDeleteByVersionId(
            @Param("contentVersionId") String contentVersionId,
            @Param("deletedAt") java.time.LocalDateTime deletedAt);
}
