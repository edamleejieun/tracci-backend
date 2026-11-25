package com.tracci.module.savedplace.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tracci.module.savedplace.entity.SavedPlaceUsage;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SavedPlaceUsageMapper extends BaseMapper<SavedPlaceUsage> {

    @Insert("""
            INSERT INTO saved_place_usages (
                id, user_id, saved_place_id, trip_id, itinerary_node_id,
                usage_type, created_at, updated_at
            ) VALUES (
                #{usage.id}, #{usage.userId}, #{usage.savedPlaceId}, #{usage.tripId},
                #{usage.itineraryNodeId}, #{usage.usageType}, #{usage.createdAt}, #{usage.updatedAt}
            )
            ON CONFLICT (saved_place_id, itinerary_node_id)
                WHERE deleted_at IS NULL AND itinerary_node_id IS NOT NULL
            DO NOTHING
            """)
    int insertNodeUsageIfAbsent(@Param("usage") SavedPlaceUsage usage);
}
