package com.tracci.module.journal.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tracci.module.journal.entity.JournalPhoto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface JournalPhotoMapper extends BaseMapper<JournalPhoto> {

    @Update("""
            UPDATE journal_photos
               SET deleted_at = NOW(),
                   is_featured = FALSE,
                   updated_at = NOW()
             WHERE journal_entry_id = #{journalEntryId}
               AND trip_id = #{tripId}
               AND user_id = #{userId}
               AND deleted_at IS NULL
            """)
    int softDeleteByJournalEntryId(
            @Param("journalEntryId") String journalEntryId,
            @Param("tripId") String tripId,
            @Param("userId") String userId);

    @Update("""
            UPDATE journal_photos
               SET deleted_at = NOW(),
                   is_featured = FALSE,
                   updated_at = NOW()
             WHERE journal_entry_id = #{journalEntryId}
               AND trip_id = #{tripId}
               AND deleted_at IS NULL
            """)
    int softDeleteAllByJournalEntryId(
            @Param("journalEntryId") String journalEntryId,
            @Param("tripId") String tripId);

    @Update("""
            UPDATE journal_photos
               SET deleted_at = NOW(),
                   is_featured = FALSE,
                   updated_at = NOW()
             WHERE trip_id = #{tripId}
               AND user_id = #{userId}
               AND deleted_at IS NULL
            """)
    int softDeleteByTripId(@Param("tripId") String tripId, @Param("userId") String userId);

    @Update("""
            UPDATE journal_photos
               SET deleted_at = NOW(),
                   is_featured = FALSE,
                   updated_at = NOW()
             WHERE trip_id = #{tripId}
               AND deleted_at IS NULL
            """)
    int softDeleteAllByTripId(@Param("tripId") String tripId);

    @Select("""
            SELECT COUNT(*)
              FROM journal_photos
             WHERE trip_id = #{tripId}
               AND is_featured = TRUE
               AND deleted_at IS NULL
            """)
    long countFeaturedByTripId(@Param("tripId") String tripId);

    @Update("""
            UPDATE journal_photos
               SET is_featured = #{featured},
                   updated_at = NOW()
             WHERE id = #{photoId}
               AND trip_id = #{tripId}
               AND deleted_at IS NULL
               AND (
                   #{featured} = FALSE
                   OR 9 > (
                       SELECT COUNT(*)
                         FROM journal_photos
                        WHERE trip_id = #{tripId}
                          AND is_featured = TRUE
                          AND deleted_at IS NULL
                   )
               )
            """)
    int updateFeatured(
            @Param("tripId") String tripId,
            @Param("photoId") String photoId,
            @Param("featured") boolean featured);

    @Update("""
            UPDATE journal_photos
               SET is_featured = FALSE,
                   updated_at = NOW()
             WHERE journal_entry_id = #{journalEntryId}
               AND trip_id = #{tripId}
               AND is_featured = TRUE
               AND deleted_at IS NULL
            """)
    int clearFeaturedByJournalEntryId(
            @Param("journalEntryId") String journalEntryId,
            @Param("tripId") String tripId);

    @Select("""
            SELECT COUNT(*)
              FROM journal_photos jp
              LEFT JOIN files f ON jp.file_id = f.id AND f.deleted_at IS NULL
             WHERE jp.user_id = #{userId}
               AND jp.deleted_at IS NULL
               AND (jp.file_id IS NULL OR f.status = 'UPLOADED')
            """)
    long countUploadedPhotoByUserId(@Param("userId") String userId);

    @Select("""
            <script>
            SELECT COUNT(*)
              FROM journal_photos jp
              JOIN files f ON jp.file_id = f.id
                          AND f.user_id = #{userId}
                          AND f.status = 'UPLOADED'
                          AND f.deleted_at IS NULL
             WHERE jp.user_id = #{userId}
               AND jp.trip_id IN
               <foreach collection="tripIds" item="tripId" open="(" separator="," close=")">
                   #{tripId}
               </foreach>
               AND jp.deleted_at IS NULL
            </script>
            """)
    long countUploadedPhotoByUserIdAndTripIds(
            @Param("userId") String userId,
            @Param("tripIds") List<String> tripIds);

    @Select("""
            <script>
            SELECT jp.trip_id,
                   COUNT(*) AS cnt
              FROM journal_photos jp
              JOIN files f ON jp.file_id = f.id
                          AND f.user_id = #{userId}
                          AND f.status = 'UPLOADED'
                          AND f.deleted_at IS NULL
             WHERE jp.user_id = #{userId}
               AND jp.trip_id IN
               <foreach collection="tripIds" item="tripId" open="(" separator="," close=")">
                   #{tripId}
               </foreach>
               AND jp.deleted_at IS NULL
             GROUP BY jp.trip_id
            </script>
            """)
    List<TripCountSummary> countUploadedPhotoByTripIdsAndUserId(
            @Param("tripIds") List<String> tripIds,
            @Param("userId") String userId);

    @Select("""
            <script>
            SELECT COUNT(*)
              FROM journal_photos jp
              JOIN files f ON jp.file_id = f.id
                          AND f.status = 'UPLOADED'
                          AND f.deleted_at IS NULL
             WHERE jp.trip_id IN
               <foreach collection="tripIds" item="tripId" open="(" separator="," close=")">
                   #{tripId}
               </foreach>
               AND jp.deleted_at IS NULL
            </script>
            """)
    long countUploadedPhotoTotalByTripIds(@Param("tripIds") List<String> tripIds);

    @Select("""
            <script>
            SELECT COUNT(*)
              FROM journal_photos jp
              JOIN journal_entries je ON je.id = jp.journal_entry_id
                                     AND je.trip_id = jp.trip_id
                                     AND je.deleted_at IS NULL
              JOIN files f ON jp.file_id = f.id
                          AND f.status = 'UPLOADED'
                          AND f.deleted_at IS NULL
             WHERE jp.trip_id IN
               <foreach collection="tripIds" item="tripId" open="(" separator="," close=")">
                   #{tripId}
               </foreach>
               AND (je.visibility = 'all' OR je.user_id = #{userId})
               AND jp.deleted_at IS NULL
            </script>
            """)
    long countVisibleUploadedPhotoByTripIdsAndUserId(
            @Param("tripIds") List<String> tripIds,
            @Param("userId") String userId);

    @Select("""
            <script>
            SELECT jp.trip_id,
                   COUNT(*) AS cnt
              FROM journal_photos jp
              JOIN files f ON jp.file_id = f.id
                          AND f.status = 'UPLOADED'
                          AND f.deleted_at IS NULL
             WHERE jp.trip_id IN
               <foreach collection="tripIds" item="tripId" open="(" separator="," close=")">
                   #{tripId}
               </foreach>
               AND jp.deleted_at IS NULL
             GROUP BY jp.trip_id
            </script>
            """)
    List<TripCountSummary> countUploadedPhotoByTripIds(@Param("tripIds") List<String> tripIds);

    @Select("""
            <script>
            SELECT jp.trip_id,
                   COUNT(*) AS cnt
              FROM journal_photos jp
              JOIN journal_entries je ON je.id = jp.journal_entry_id
                                     AND je.trip_id = jp.trip_id
                                     AND je.deleted_at IS NULL
              JOIN files f ON jp.file_id = f.id
                          AND f.status = 'UPLOADED'
                          AND f.deleted_at IS NULL
             WHERE jp.trip_id IN
               <foreach collection="tripIds" item="tripId" open="(" separator="," close=")">
                   #{tripId}
               </foreach>
               AND (je.visibility = 'all' OR je.user_id = #{userId})
               AND jp.deleted_at IS NULL
             GROUP BY jp.trip_id
            </script>
            """)
    List<TripCountSummary> countVisibleUploadedPhotoGroupByTripIdsAndUserId(
            @Param("tripIds") List<String> tripIds,
            @Param("userId") String userId);

    record TripCountSummary(String tripId, long cnt) {
    }
}
