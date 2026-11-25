package com.tracci.module.trip.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tracci.module.trip.entity.Trip;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface TripMapper extends BaseMapper<Trip> {

    @Select("""
            SELECT *
              FROM trips
             WHERE id = #{tripId}
               AND deleted_at IS NULL
             FOR UPDATE
            """)
    Trip lockByIdForUpdate(@Param("tripId") String tripId);

    @Select("""
            SELECT *
              FROM trips
             WHERE user_id = #{userId}
               AND deleted_at IS NULL
             ORDER BY id
             FOR UPDATE
            """)
    List<Trip> lockOwnedTripsForUpdate(@Param("userId") String userId);

    @Select("""
            <script>
            SELECT t.*
              FROM trips t
              JOIN trip_members tm
                ON tm.trip_id = t.id
               AND tm.user_id = #{userId}
               AND tm.status = 'active'
               AND tm.deleted_at IS NULL
             WHERE t.deleted_at IS NULL
             <if test="status == 'planning'">
               AND t.start_date &gt; #{today}
             </if>
             <if test="status == 'ongoing'">
               AND t.start_date &lt;= #{today}
               AND t.end_date &gt;= #{today}
             </if>
             <if test="status == 'completed'">
               AND t.end_date &lt; #{today}
             </if>
             <if test="keyword != null and keyword != ''">
               AND (
                   LOWER(t.title) LIKE CONCAT('%', LOWER(#{keyword}), '%')
                   OR LOWER(COALESCE(t.destination_name, '')) LIKE CONCAT('%', LOWER(#{keyword}), '%')
               )
             </if>
             ORDER BY t.created_at DESC
            </script>
            """)
    Page<Trip> pageMemberTrips(
            Page<?> page,
            @Param("userId") String userId,
            @Param("status") String status,
            @Param("keyword") String keyword,
            @Param("today") LocalDate today);

    @Select("""
            SELECT t.*
              FROM trips t
              JOIN trip_members tm
                ON tm.trip_id = t.id
               AND tm.user_id = #{userId}
               AND tm.status = 'active'
               AND tm.deleted_at IS NULL
             WHERE t.deleted_at IS NULL
             ORDER BY t.created_at DESC
            """)
    List<Trip> listMemberTrips(@Param("userId") String userId);

    @Select("""
            SELECT t.*
              FROM trips t
              JOIN trip_members tm
                ON tm.trip_id = t.id
               AND tm.user_id = #{userId}
               AND tm.status = 'active'
               AND tm.deleted_at IS NULL
             WHERE t.deleted_at IS NULL
               AND t.start_date IS NOT NULL
               AND t.start_date <= #{today}
             ORDER BY t.start_date DESC, t.created_at DESC
            """)
    List<Trip> listStartedMemberTrips(@Param("userId") String userId, @Param("today") LocalDate today);

    @Update("""
            UPDATE trips
               SET share_enabled = #{shareEnabled},
                   updated_at = NOW()
             WHERE id = #{tripId}
               AND deleted_at IS NULL
            """)
    int updateShareEnabled(@Param("tripId") String tripId, @Param("shareEnabled") boolean shareEnabled);

    @Update("""
            UPDATE trips
               SET user_id = #{userId},
                   updated_at = NOW()
             WHERE id = #{tripId}
               AND deleted_at IS NULL
            """)
    int updateOwnerUserId(@Param("tripId") String tripId, @Param("userId") String userId);

    @Select("""
            SELECT COUNT(*)
              FROM trips t
             WHERE t.user_id = #{userId}
               AND t.deleted_at IS NULL
               AND EXISTS (
                   SELECT 1
                     FROM trip_members tm
                     JOIN users u
                       ON u.id = tm.user_id
                      AND u.status = 'active'
                      AND u.deleted_at IS NULL
                    WHERE tm.trip_id = t.id
                      AND tm.user_id <> #{userId}
                      AND tm.status = 'active'
                      AND tm.deleted_at IS NULL
               )
            """)
    long countOwnedTripsWithOtherEffectiveMembers(@Param("userId") String userId);
}
