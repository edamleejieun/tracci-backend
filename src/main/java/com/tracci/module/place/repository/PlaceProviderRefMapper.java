package com.tracci.module.place.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tracci.module.place.entity.PlaceProviderRef;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PlaceProviderRefMapper extends BaseMapper<PlaceProviderRef> {

    /**
     * PostgreSQL 原子占用供应商身份。返回 0 表示身份已被另一个 Place 占用。
     */
    @Insert("""
            INSERT INTO place_provider_refs (
                id, place_id, provider, provider_place_id, is_primary,
                last_checked_at, created_at, updated_at
            ) VALUES (
                #{ref.id}, #{ref.placeId}, #{ref.provider}, #{ref.providerPlaceId}, #{ref.isPrimary},
                #{ref.lastCheckedAt}, #{ref.createdAt}, #{ref.updatedAt}
            )
            ON CONFLICT (provider, provider_place_id) WHERE deleted_at IS NULL
            DO NOTHING
            """)
    int insertIfAbsent(@Param("ref") PlaceProviderRef ref);
}
