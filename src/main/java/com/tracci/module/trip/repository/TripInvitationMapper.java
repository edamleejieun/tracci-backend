package com.tracci.module.trip.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tracci.module.trip.entity.TripInvitation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface TripInvitationMapper extends BaseMapper<TripInvitation> {

    @Select("""
            SELECT *
              FROM trip_invitations
             WHERE invite_code_hash = #{inviteCodeHash}
               AND deleted_at IS NULL
             LIMIT 1
            """)
    TripInvitation findByCodeHash(@Param("inviteCodeHash") String inviteCodeHash);

    @Update("""
            UPDATE trip_invitations
               SET used_count = used_count + 1,
                   updated_at = NOW()
             WHERE id = #{id}
               AND used_count < max_uses
               AND deleted_at IS NULL
            """)
    int incrementUsedCount(@Param("id") String id);
}
