package com.tracci.module.trip.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tracci.module.trip.entity.ItineraryNode;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface ItineraryNodeMapper extends BaseMapper<ItineraryNode> {

    @Select("""
            SELECT COALESCE(MAX(sort_order), 0)
              FROM itinerary_nodes
             WHERE trip_day_id = #{dayId}
               AND trip_id = #{tripId}
               AND user_id = #{userId}
               AND deleted_at IS NULL
            """)
    Integer selectMaxSortOrder(
            @Param("tripId") String tripId,
            @Param("dayId") String dayId,
            @Param("userId") String userId);

    @Select("""
            SELECT COALESCE(MAX(sort_order), 0)
              FROM itinerary_nodes
             WHERE trip_day_id = #{dayId}
               AND trip_id = #{tripId}
               AND deleted_at IS NULL
            """)
    Integer selectMaxSortOrderByTripDay(
            @Param("tripId") String tripId,
            @Param("dayId") String dayId);

    /**
     * 逻辑节点计数：hotel 中间夜投影（stayRole=stay）不计入，避免连住抬高统计。
     */
    @Select("""
            SELECT COUNT(*)
              FROM itinerary_nodes
             WHERE trip_id = #{tripId}
               AND user_id = #{userId}
               AND deleted_at IS NULL
               AND NOT (
                   type = 'hotel'
                   AND COALESCE(details_json->>'stayRole', '') = 'stay'
               )
            """)
    long countByTripIdAndUserId(@Param("tripId") String tripId, @Param("userId") String userId);

    @Select("""
            SELECT COUNT(*)
              FROM itinerary_nodes
             WHERE trip_id = #{tripId}
               AND deleted_at IS NULL
               AND NOT (
                   type = 'hotel'
                   AND COALESCE(details_json->>'stayRole', '') = 'stay'
               )
            """)
    long countByTripId(@Param("tripId") String tripId);

    @Select("""
            SELECT COUNT(DISTINCT COALESCE(NULLIF(TRIM(location_name), ''), NULLIF(TRIM(address), ''), NULLIF(TRIM(title), '')))
              FROM itinerary_nodes
             WHERE trip_id = #{tripId}
               AND deleted_at IS NULL
               AND COALESCE(NULLIF(TRIM(location_name), ''), NULLIF(TRIM(address), ''), NULLIF(TRIM(title), '')) IS NOT NULL
            """)
    long countDistinctPlacesByTripId(@Param("tripId") String tripId);

    @Select("""
            SELECT COUNT(*)
              FROM itinerary_nodes
             WHERE user_id = #{userId}
               AND deleted_at IS NULL
               AND NOT (
                   type = 'hotel'
                   AND COALESCE(details_json->>'stayRole', '') = 'stay'
               )
            """)
    long countByUserId(@Param("userId") String userId);

    @Select("""
            <script>
            SELECT COUNT(*)
              FROM itinerary_nodes
             WHERE user_id = #{userId}
               AND trip_id IN
               <foreach collection="tripIds" item="tripId" open="(" separator="," close=")">
                   #{tripId}
               </foreach>
               AND deleted_at IS NULL
               AND NOT (
                   type = 'hotel'
                   AND COALESCE(details_json->>'stayRole', '') = 'stay'
               )
            </script>
            """)
    long countByUserIdAndTripIds(
            @Param("userId") String userId,
            @Param("tripIds") List<String> tripIds);

    @Select("""
            SELECT n.title
              FROM itinerary_nodes n
              LEFT JOIN trip_days d
                ON d.id = n.trip_day_id
               AND d.trip_id = n.trip_id
               AND d.user_id = n.user_id
               AND d.deleted_at IS NULL
             WHERE n.trip_id = #{tripId}
               AND n.user_id = #{userId}
               AND n.type = #{nodeType}
               AND n.title IS NOT NULL
               AND TRIM(n.title) <> ''
               AND n.deleted_at IS NULL
             GROUP BY n.title
             ORDER BY MIN(COALESCE(d.day_index, 2147483647)),
                      MIN(COALESCE(n.sort_order, 2147483647)),
                      MIN(n.created_at),
                      MIN(n.id)
            """)
    List<String> selectAttractionTitlesByTripIdAndUserId(
            @Param("tripId") String tripId,
            @Param("userId") String userId,
            @Param("nodeType") String nodeType);

    @Select("""
            <script>
            SELECT trip_id,
                   COUNT(*) AS cnt
              FROM itinerary_nodes
             WHERE user_id = #{userId}
               AND trip_id IN
               <foreach collection="tripIds" item="tripId" open="(" separator="," close=")">
                   #{tripId}
               </foreach>
               AND deleted_at IS NULL
               AND NOT (
                   type = 'hotel'
                   AND COALESCE(details_json->>'stayRole', '') = 'stay'
               )
             GROUP BY trip_id
            </script>
            """)
    List<TripCountSummary> countByTripIdsAndUserId(
            @Param("tripIds") List<String> tripIds,
            @Param("userId") String userId);

    @Select("""
            <script>
            SELECT trip_id,
                   COUNT(*) AS cnt
              FROM itinerary_nodes
             WHERE trip_id IN
               <foreach collection="tripIds" item="tripId" open="(" separator="," close=")">
                   #{tripId}
               </foreach>
               AND deleted_at IS NULL
               AND NOT (
                   type = 'hotel'
                   AND COALESCE(details_json->>'stayRole', '') = 'stay'
               )
             GROUP BY trip_id
            </script>
            """)
    List<TripCountSummary> countGroupByTripIds(@Param("tripIds") List<String> tripIds);

    @Select("""
            <script>
            SELECT COUNT(*)
              FROM itinerary_nodes
             WHERE trip_id IN
               <foreach collection="tripIds" item="tripId" open="(" separator="," close=")">
                   #{tripId}
               </foreach>
               AND deleted_at IS NULL
               AND NOT (
                   type = 'hotel'
                   AND COALESCE(details_json->>'stayRole', '') = 'stay'
               )
            </script>
            """)
    long countAllByTripIds(@Param("tripIds") List<String> tripIds);

    @Select("""
            <script>
            SELECT COALESCE(NULLIF(TRIM(type), ''), 'other') AS type,
                   COUNT(*) AS cnt
              FROM itinerary_nodes
             WHERE user_id = #{userId}
               AND trip_id IN
               <foreach collection="tripIds" item="tripId" open="(" separator="," close=")">
                   #{tripId}
               </foreach>
               AND deleted_at IS NULL
               AND NOT (
                   type = 'hotel'
                   AND COALESCE(details_json->>'stayRole', '') = 'stay'
               )
             GROUP BY COALESCE(NULLIF(TRIM(type), ''), 'other')
             ORDER BY cnt DESC
            </script>
            """)
    List<NodeTypeSummary> nodeTypeSummaryByUserIdAndTripIds(
            @Param("userId") String userId,
            @Param("tripIds") List<String> tripIds);

    @Select("""
            <script>
            SELECT COALESCE(NULLIF(TRIM(type), ''), 'other') AS type,
                   COUNT(*) AS cnt
              FROM itinerary_nodes
             WHERE trip_id IN
               <foreach collection="tripIds" item="tripId" open="(" separator="," close=")">
                   #{tripId}
               </foreach>
               AND deleted_at IS NULL
               AND NOT (
                   type = 'hotel'
                   AND COALESCE(details_json->>'stayRole', '') = 'stay'
               )
             GROUP BY COALESCE(NULLIF(TRIM(type), ''), 'other')
             ORDER BY cnt DESC
            </script>
            """)
    List<NodeTypeSummary> nodeTypeSummaryByTripIds(@Param("tripIds") List<String> tripIds);

    @Select("""
            <script>
            SELECT trip_id,
                   COUNT(*) AS cnt
              FROM itinerary_nodes
             WHERE user_id = #{userId}
               AND trip_id IN
               <foreach collection="tripIds" item="tripId" open="(" separator="," close=")">
                   #{tripId}
               </foreach>
               AND deleted_at IS NULL
               AND (
                   COALESCE(NULLIF(TRIM(location_name), ''), NULLIF(TRIM(address), '')) IS NULL
                   OR location_latitude IS NULL
                   OR location_longitude IS NULL
               )
             GROUP BY trip_id
            </script>
            """)
    List<TripCountSummary> countMissingLocationByTripIdsAndUserId(
            @Param("tripIds") List<String> tripIds,
            @Param("userId") String userId);

    @Select("""
            <script>
            SELECT trip_id,
                   COUNT(*) AS cnt
              FROM itinerary_nodes
             WHERE trip_id IN
               <foreach collection="tripIds" item="tripId" open="(" separator="," close=")">
                   #{tripId}
               </foreach>
               AND deleted_at IS NULL
               AND (
                   COALESCE(NULLIF(TRIM(location_name), ''), NULLIF(TRIM(address), '')) IS NULL
                   OR location_latitude IS NULL
                   OR location_longitude IS NULL
               )
             GROUP BY trip_id
            </script>
            """)
    List<TripCountSummary> countMissingLocationByTripIds(@Param("tripIds") List<String> tripIds);

    @Update("""
            UPDATE itinerary_nodes
               SET deleted_at = NOW(),
                   updated_at = NOW()
             WHERE trip_id = #{tripId}
               AND user_id = #{userId}
               AND deleted_at IS NULL
            """)
    int softDeleteByTripId(@Param("tripId") String tripId, @Param("userId") String userId);

    @Update("""
            UPDATE itinerary_nodes
               SET deleted_at = NOW(),
                   updated_at = NOW()
             WHERE trip_id = #{tripId}
               AND deleted_at IS NULL
            """)
    int softDeleteAllByTripId(@Param("tripId") String tripId);

    @Update("""
            UPDATE itinerary_nodes
               SET deleted_at = NOW(),
                   updated_at = NOW()
             WHERE trip_id = #{tripId}
               AND user_id = #{userId}
               AND trip_day_id IN (
                   SELECT id
                     FROM trip_days
                    WHERE trip_id = #{tripId}
                      AND user_id = #{userId}
                      AND day_index > #{maxDayIndex}
               )
               AND deleted_at IS NULL
            """)
    int softDeleteAfterDayIndex(
            @Param("tripId") String tripId,
            @Param("userId") String userId,
            @Param("maxDayIndex") int maxDayIndex);

    record TripCountSummary(String tripId, long cnt) {
    }

    record NodeTypeSummary(String type, long cnt) {
    }
}
