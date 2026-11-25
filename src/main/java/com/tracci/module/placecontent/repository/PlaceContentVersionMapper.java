package com.tracci.module.placecontent.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tracci.module.placecontent.entity.PlaceContentVersion;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 地点内容版本数据访问。
 */
@Mapper
public interface PlaceContentVersionMapper extends BaseMapper<PlaceContentVersion> {

    /** 行锁读取版本，用于发布事务内串行化。 */
    @Select("""
            SELECT * FROM place_content_versions
            WHERE id = #{id} AND deleted_at IS NULL
            FOR UPDATE
            """)
    PlaceContentVersion selectByIdForUpdate(@Param("id") String id);

    /**
     * 将指定 Place + locale 下当前 PUBLISHED 版本改为 ARCHIVED。
     * 返回受影响行数：0 表示无 PUBLISHED 版本（首次发布），1 表示已归档旧版本。
     */
    @Update("""
            UPDATE place_content_versions
            SET status = 'ARCHIVED', archived_at = #{archivedAt}, updated_at = #{archivedAt}
            WHERE place_id = #{placeId}
              AND locale = #{locale}
              AND status = 'PUBLISHED'
              AND deleted_at IS NULL
            """)
    int archiveCurrentPublished(
            @Param("placeId") String placeId,
            @Param("locale") String locale,
            @Param("archivedAt") java.time.LocalDateTime archivedAt);

    /**
     * 查询指定 Place + locale 下最大的 version_no，用于单调递增。
     * 无记录时返回 null。
     */
    @Select("""
            SELECT MAX(version_no) FROM place_content_versions
            WHERE place_id = #{placeId} AND locale = #{locale} AND deleted_at IS NULL
            """)
    Integer selectMaxVersionNo(@Param("placeId") String placeId, @Param("locale") String locale);

    /**
     * 按 placeId + locale + sourceDigest 查找已有的 DRAFT（幂等复用）。
     * 无匹配时返回 null。
     */
    @Select("""
            SELECT * FROM place_content_versions
            WHERE place_id = #{placeId}
              AND locale = #{locale}
              AND source_digest = #{sourceDigest}
              AND prompt_version = #{promptVersion}
              AND status = 'DRAFT'
              AND deleted_at IS NULL
            ORDER BY created_at DESC
            LIMIT 1
            """)
    PlaceContentVersion selectDraftBySourceDigest(
            @Param("placeId") String placeId,
            @Param("locale") String locale,
            @Param("sourceDigest") String sourceDigest,
            @Param("promptVersion") String promptVersion);

    @Select("""
            SELECT * FROM place_content_versions
            WHERE place_id = #{placeId}
              AND generation_idempotency_key = #{idempotencyKey}
              AND deleted_at IS NULL
            LIMIT 1
            """)
    PlaceContentVersion selectByGenerationIdempotencyKey(
            @Param("placeId") String placeId,
            @Param("idempotencyKey") String idempotencyKey);

    @Select("""
            SELECT * FROM place_content_versions
            WHERE publish_idempotency_key = #{idempotencyKey}
              AND deleted_at IS NULL
            LIMIT 1
            """)
    PlaceContentVersion selectByPublishIdempotencyKey(
            @Param("idempotencyKey") String idempotencyKey);

    @Select("""
            SELECT * FROM place_content_versions
            WHERE place_id = #{placeId}
              AND locale = #{locale}
              AND status = 'PUBLISHED'
              AND deleted_at IS NULL
            LIMIT 1
            FOR UPDATE
            """)
    PlaceContentVersion selectCurrentPublishedForUpdate(
            @Param("placeId") String placeId,
            @Param("locale") String locale);

    @Update("""
            UPDATE place_content_versions
            SET status = 'ARCHIVED', archived_at = #{archivedAt}, updated_at = #{archivedAt}
            WHERE place_id = #{placeId}
              AND locale = #{locale}
              AND source_digest = #{sourceDigest}
              AND prompt_version = #{promptVersion}
              AND status = 'DRAFT'
              AND deleted_at IS NULL
            """)
    int archiveDraftBySourceDigest(
            @Param("placeId") String placeId,
            @Param("locale") String locale,
            @Param("sourceDigest") String sourceDigest,
            @Param("promptVersion") String promptVersion,
            @Param("archivedAt") java.time.LocalDateTime archivedAt);
}
