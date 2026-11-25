package com.tracci.module.place.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tracci.module.place.entity.Place;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface PlaceMapper extends BaseMapper<Place> {

    @Select("""
            SELECT * FROM places
            WHERE id = #{id} AND deleted_at IS NULL
            FOR UPDATE
            """)
    Place selectByIdForUpdate(@Param("id") String id);

    @Select("""
            <script>
            SELECT COUNT(*)
            FROM places p
            INNER JOIN place_content_versions pcv
                    ON pcv.place_id = p.id
                   AND pcv.status = 'PUBLISHED'
                   AND pcv.locale = 'zh-CN'
                   AND pcv.deleted_at IS NULL
            WHERE p.status = 'ACTIVE'
              AND p.deleted_at IS NULL
              <if test="placeScope != null and placeScope != ''">
                AND p.place_scope = #{placeScope}
              </if>
              <if test="cityName != null and cityName != ''">
                AND p.city_name = #{cityName}
              </if>
              <if test="category != null and category != ''">
                AND p.category = #{category}
              </if>
            </script>
            """)
    long countPublishedPlaces(
            @Param("placeScope") String placeScope,
            @Param("cityName") String cityName,
            @Param("category") String category);

    @Select("""
            <script>
            SELECT
                p.id,
                p.place_scope,
                p.category,
                p.canonical_name,
                p.city_name,
                p.country,
                p.latitude,
                p.longitude,
                pcv.published_at,
                pcv.highlights_json,
                pcv.introduction
            FROM places p
            INNER JOIN place_content_versions pcv
                    ON pcv.place_id = p.id
                   AND pcv.status = 'PUBLISHED'
                   AND pcv.locale = 'zh-CN'
                   AND pcv.deleted_at IS NULL
            WHERE p.status = 'ACTIVE'
              AND p.deleted_at IS NULL
              <if test="placeScope != null and placeScope != ''">
                AND p.place_scope = #{placeScope}
              </if>
              <if test="cityName != null and cityName != ''">
                AND p.city_name = #{cityName}
              </if>
              <if test="category != null and category != ''">
                AND p.category = #{category}
              </if>
            ORDER BY p.city_name COLLATE "C" ASC NULLS LAST,
                     pcv.published_at DESC,
                     p.id ASC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<PublishedPlaceRow> selectPublishedPlaces(
            @Param("placeScope") String placeScope,
            @Param("cityName") String cityName,
            @Param("category") String category,
            @Param("limit") int limit,
            @Param("offset") long offset);
}
