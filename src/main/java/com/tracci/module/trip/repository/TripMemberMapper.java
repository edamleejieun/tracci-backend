package com.tracci.module.trip.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tracci.module.trip.entity.TripMember;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface TripMemberMapper extends BaseMapper<TripMember> {

    @Select("""
            SELECT tm.*
              FROM trip_members tm
              JOIN users u
                ON u.id = tm.user_id
               AND u.status = 'active'
               AND u.deleted_at IS NULL
             WHERE tm.trip_id = #{tripId}
               AND tm.user_id = #{userId}
               AND tm.status = 'active'
               AND tm.deleted_at IS NULL
             LIMIT 1
            """)
    TripMember findActiveByTripIdAndUserId(@Param("tripId") String tripId, @Param("userId") String userId);

    @Select("""
            SELECT *
              FROM trip_members
             WHERE trip_id = #{tripId}
               AND user_id = #{userId}
             ORDER BY deleted_at NULLS FIRST, updated_at DESC
             LIMIT 1
            """)
    TripMember findAnyByTripIdAndUserId(@Param("tripId") String tripId, @Param("userId") String userId);

    @Select("""
            SELECT *
              FROM trip_members
             WHERE id = #{memberId}
               AND trip_id = #{tripId}
               AND status = 'active'
               AND deleted_at IS NULL
             LIMIT 1
            """)
    TripMember findActiveByIdAndTripId(@Param("memberId") String memberId, @Param("tripId") String tripId);

    @Select("""
            SELECT tm.*
              FROM trip_members tm
              JOIN users u
                ON u.id = tm.user_id
               AND u.status = 'active'
               AND u.deleted_at IS NULL
             WHERE tm.id = #{memberId}
               AND tm.trip_id = #{tripId}
               AND tm.status = 'active'
               AND tm.deleted_at IS NULL
             LIMIT 1
            """)
    TripMember findEffectiveActiveByIdAndTripId(
            @Param("memberId") String memberId,
            @Param("tripId") String tripId);

    @Select("""
            SELECT *
              FROM trip_members
             WHERE trip_id = #{tripId}
               AND status = 'active'
               AND deleted_at IS NULL
             ORDER BY joined_at ASC, created_at ASC
            """)
    List<TripMember> listActiveByTripId(@Param("tripId") String tripId);

    @Select("""
            SELECT COUNT(*)
              FROM trip_members tm
              JOIN users u
                ON u.id = tm.user_id
               AND u.status = 'active'
               AND u.deleted_at IS NULL
             WHERE tm.trip_id = #{tripId}
               AND tm.status = 'active'
               AND tm.deleted_at IS NULL
            """)
    long countEffectiveActiveByTripId(@Param("tripId") String tripId);

    @Select("""
            SELECT COUNT(*)
              FROM trip_members tm
              JOIN users u
                ON u.id = tm.user_id
               AND u.status = 'active'
               AND u.deleted_at IS NULL
             WHERE tm.trip_id = #{tripId}
               AND tm.role = 'owner'
               AND tm.status = 'active'
               AND tm.deleted_at IS NULL
            """)
    long countEffectiveActiveOwnersByTripId(@Param("tripId") String tripId);

    @Update("""
            UPDATE trip_members
               SET role = #{role},
                   status = 'active',
                   invited_by_user_id = #{invitedByUserId},
                   joined_at = #{joinedAt},
                   deleted_at = NULL,
                   updated_at = NOW()
             WHERE id = #{memberId}
            """)
    int restoreMember(
            @Param("memberId") String memberId,
            @Param("role") String role,
            @Param("invitedByUserId") String invitedByUserId,
            @Param("joinedAt") LocalDateTime joinedAt);

    @Update("""
            UPDATE trip_members
               SET role = #{role},
                   updated_at = NOW()
             WHERE id = #{memberId}
               AND trip_id = #{tripId}
               AND status = 'active'
               AND deleted_at IS NULL
            """)
    int updateRole(@Param("tripId") String tripId, @Param("memberId") String memberId, @Param("role") String role);

    @Update("""
            UPDATE trip_members
               SET role = 'editor',
                   updated_at = NOW()
             WHERE id = #{memberId}
               AND trip_id = #{tripId}
               AND role = 'owner'
               AND status = 'active'
               AND deleted_at IS NULL
            """)
    int demoteOwner(@Param("tripId") String tripId, @Param("memberId") String memberId);

    @Update("""
            UPDATE trip_members
               SET role = 'owner',
                   updated_at = NOW()
             WHERE id = #{memberId}
               AND trip_id = #{tripId}
               AND role <> 'owner'
               AND status = 'active'
               AND deleted_at IS NULL
            """)
    int promoteOwner(@Param("tripId") String tripId, @Param("memberId") String memberId);

    @Update("""
            UPDATE trip_members
               SET status = 'removed',
                   deleted_at = NOW(),
                   updated_at = NOW()
             WHERE id = #{memberId}
               AND trip_id = #{tripId}
               AND status = 'active'
               AND deleted_at IS NULL
            """)
    int removeMember(@Param("tripId") String tripId, @Param("memberId") String memberId);

    @Update("""
            UPDATE trip_members
               SET status = 'removed',
                   deleted_at = NOW(),
                   updated_at = NOW()
             WHERE trip_id = #{tripId}
               AND user_id = #{userId}
               AND deleted_at IS NULL
            """)
    int softDeleteByTripId(@Param("tripId") String tripId, @Param("userId") String userId);

    @Update("""
            UPDATE trip_members
               SET status = 'removed',
                   deleted_at = NOW(),
                   updated_at = NOW()
             WHERE trip_id = #{tripId}
               AND deleted_at IS NULL
            """)
    int softDeleteAllByTripId(@Param("tripId") String tripId);
}
