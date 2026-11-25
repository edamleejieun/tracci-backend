package com.tracci.module.trip.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tracci.module.trip.entity.TripDay;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDate;

@Mapper
public interface TripDayMapper extends BaseMapper<TripDay> {

    @Update("""
            UPDATE trip_days
               SET date = #{date},
                   deleted_at = NULL,
                   updated_at = NOW()
             WHERE trip_id = #{tripId}
               AND user_id = #{userId}
               AND day_index = #{dayIndex}
            """)
    int restoreOrUpdateDay(
            @Param("tripId") String tripId,
            @Param("userId") String userId,
            @Param("dayIndex") int dayIndex,
            @Param("date") LocalDate date);

    @Update("""
            UPDATE trip_days
               SET deleted_at = NOW(),
                   updated_at = NOW()
             WHERE trip_id = #{tripId}
               AND user_id = #{userId}
               AND day_index > #{maxDayIndex}
               AND deleted_at IS NULL
            """)
    int softDeleteAfterDayIndex(
            @Param("tripId") String tripId,
            @Param("userId") String userId,
            @Param("maxDayIndex") int maxDayIndex);

    @Update("""
            UPDATE trip_days
               SET deleted_at = NOW(),
                   updated_at = NOW()
             WHERE trip_id = #{tripId}
               AND user_id = #{userId}
               AND deleted_at IS NULL
            """)
    int softDeleteByTripId(@Param("tripId") String tripId, @Param("userId") String userId);

    @Update("""
            UPDATE trip_days
               SET deleted_at = NOW(),
                   updated_at = NOW()
             WHERE trip_id = #{tripId}
               AND deleted_at IS NULL
            """)
    int softDeleteAllByTripId(@Param("tripId") String tripId);
}
