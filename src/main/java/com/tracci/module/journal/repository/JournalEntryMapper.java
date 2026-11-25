package com.tracci.module.journal.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tracci.module.journal.entity.JournalEntry;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface JournalEntryMapper extends BaseMapper<JournalEntry> {

    @Update("""
            UPDATE journal_entries
               SET deleted_at = NOW(),
                   updated_at = NOW()
             WHERE trip_id = #{tripId}
               AND user_id = #{userId}
               AND deleted_at IS NULL
            """)
    int softDeleteByTripId(@Param("tripId") String tripId, @Param("userId") String userId);

    @Update("""
            UPDATE journal_entries
               SET deleted_at = NOW(),
                   updated_at = NOW()
             WHERE trip_id = #{tripId}
               AND deleted_at IS NULL
            """)
    int softDeleteAllByTripId(@Param("tripId") String tripId);

    @Select("""
            SELECT COUNT(*)
              FROM journal_entries
             WHERE trip_id = #{tripId}
               AND user_id = #{userId}
               AND deleted_at IS NULL
            """)
    long countByTripIdAndUserId(@Param("tripId") String tripId, @Param("userId") String userId);

    @Select("""
            SELECT COUNT(*)
              FROM journal_entries
             WHERE trip_id = #{tripId}
               AND deleted_at IS NULL
            """)
    long countByTripId(@Param("tripId") String tripId);

    @Select("""
            SELECT COUNT(*)
              FROM journal_entries
             WHERE trip_id = #{tripId}
               AND (visibility = 'all' OR user_id = #{userId})
               AND deleted_at IS NULL
            """)
    long countVisibleByTripIdAndUserId(
            @Param("tripId") String tripId,
            @Param("userId") String userId);

    @Select("""
            SELECT COUNT(*)
              FROM journal_entries
             WHERE trip_id = #{tripId}
               AND visibility = 'all'
               AND deleted_at IS NULL
            """)
    long countPublicByTripId(@Param("tripId") String tripId);

    @Select("""
            SELECT COUNT(*)
              FROM journal_entries
             WHERE user_id = #{userId}
               AND deleted_at IS NULL
            """)
    long countByUserId(@Param("userId") String userId);

    @Select("""
            <script>
            SELECT COUNT(*)
              FROM journal_entries
             WHERE user_id = #{userId}
               AND trip_id IN
               <foreach collection="tripIds" item="tripId" open="(" separator="," close=")">
                   #{tripId}
               </foreach>
               AND deleted_at IS NULL
            </script>
            """)
    long countByUserIdAndTripIds(
            @Param("userId") String userId,
            @Param("tripIds") List<String> tripIds);

    @Select("""
            <script>
            SELECT trip_id,
                   COUNT(*) AS cnt
              FROM journal_entries
             WHERE user_id = #{userId}
               AND trip_id IN
               <foreach collection="tripIds" item="tripId" open="(" separator="," close=")">
                   #{tripId}
               </foreach>
               AND deleted_at IS NULL
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
              FROM journal_entries
             WHERE trip_id IN
               <foreach collection="tripIds" item="tripId" open="(" separator="," close=")">
                   #{tripId}
               </foreach>
               AND deleted_at IS NULL
             GROUP BY trip_id
            </script>
            """)
    List<TripCountSummary> countGroupByTripIds(@Param("tripIds") List<String> tripIds);

    @Select("""
            <script>
            SELECT COUNT(*)
              FROM journal_entries
             WHERE trip_id IN
               <foreach collection="tripIds" item="tripId" open="(" separator="," close=")">
                   #{tripId}
               </foreach>
               AND deleted_at IS NULL
            </script>
            """)
    long countByTripIds(@Param("tripIds") List<String> tripIds);

    @Select("""
            <script>
            SELECT COUNT(*)
              FROM journal_entries
             WHERE trip_id IN
               <foreach collection="tripIds" item="tripId" open="(" separator="," close=")">
                   #{tripId}
               </foreach>
               AND (visibility = 'all' OR user_id = #{userId})
               AND deleted_at IS NULL
            </script>
            """)
    long countVisibleByTripIdsAndUserId(
            @Param("tripIds") List<String> tripIds,
            @Param("userId") String userId);

    @Select("""
            <script>
            SELECT trip_id,
                   COUNT(*) AS cnt
              FROM journal_entries
             WHERE trip_id IN
               <foreach collection="tripIds" item="tripId" open="(" separator="," close=")">
                   #{tripId}
               </foreach>
               AND (visibility = 'all' OR user_id = #{userId})
               AND deleted_at IS NULL
             GROUP BY trip_id
            </script>
            """)
    List<TripCountSummary> countVisibleGroupByTripIdsAndUserId(
            @Param("tripIds") List<String> tripIds,
            @Param("userId") String userId);

    @Select("""
            <script>
            SELECT trip_id,
                   COUNT(*) AS cnt
              FROM journal_entries
             WHERE user_id = #{userId}
               AND trip_id IN
               <foreach collection="tripIds" item="tripId" open="(" separator="," close=")">
                   #{tripId}
               </foreach>
               AND deleted_at IS NULL
               AND (
                   location_name IS NULL OR TRIM(location_name) = ''
                   OR latitude IS NULL
                   OR longitude IS NULL
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
              FROM journal_entries
             WHERE trip_id IN
               <foreach collection="tripIds" item="tripId" open="(" separator="," close=")">
                   #{tripId}
               </foreach>
               AND deleted_at IS NULL
               AND (
                   location_name IS NULL OR TRIM(location_name) = ''
                   OR latitude IS NULL
                   OR longitude IS NULL
               )
             GROUP BY trip_id
            </script>
            """)
    List<TripCountSummary> countMissingLocationByTripIds(@Param("tripIds") List<String> tripIds);

    record TripCountSummary(String tripId, long cnt) {
    }
}
