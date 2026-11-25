package com.tracci.module.trip.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tracci.module.trip.entity.TripActivityLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface TripActivityLogMapper extends BaseMapper<TripActivityLog> {

    @Select("""
            SELECT tal.*
              FROM trip_activity_logs tal
             WHERE tal.trip_id = #{tripId}
               AND tal.deleted_at IS NULL
               AND UPPER(tal.target_type) IN ('TRIP', 'ITINERARY_NODE', 'EXPENSE', 'JOURNAL', 'JOURNAL_PHOTO')
               AND UPPER(tal.action) <> 'TRIP_DELETED'
               AND (
                    UPPER(tal.target_type) <> 'EXPENSE'
                    OR tal.actor_user_id = #{userId}
                    OR EXISTS (
                        SELECT 1
                          FROM expenses e
                         WHERE e.id = tal.target_id
                           AND e.trip_id = #{tripId}
                           AND e.expense_scope = 'group'
                           AND e.deleted_at IS NULL
                    )
               )
               AND (
                    UPPER(tal.target_type) NOT IN ('JOURNAL', 'JOURNAL_PHOTO')
                    OR tal.actor_user_id = #{userId}
                    OR (
                        UPPER(tal.target_type) = 'JOURNAL'
                        AND EXISTS (
                            SELECT 1
                              FROM journal_entries je
                             WHERE je.id = tal.target_id
                               AND je.trip_id = #{tripId}
                               AND je.visibility = 'all'
                               AND je.deleted_at IS NULL
                        )
                    )
                    OR (
                        UPPER(tal.target_type) = 'JOURNAL_PHOTO'
                        AND EXISTS (
                            SELECT 1
                              FROM journal_photos jp
                              JOIN journal_entries je
                                ON je.id = jp.journal_entry_id
                               AND je.trip_id = jp.trip_id
                               AND je.deleted_at IS NULL
                             WHERE jp.id = tal.target_id
                               AND jp.trip_id = #{tripId}
                               AND jp.deleted_at IS NULL
                               AND je.visibility = 'all'
                        )
                    )
               )
             ORDER BY tal.created_at DESC, tal.id DESC
            """)
    Page<TripActivityLog> pageJourneyHistoryByTripId(
            Page<TripActivityLog> page,
            @Param("tripId") String tripId,
            @Param("userId") String userId);
}
