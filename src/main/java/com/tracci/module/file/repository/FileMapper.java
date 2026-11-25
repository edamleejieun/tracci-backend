package com.tracci.module.file.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tracci.module.file.entity.File;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface FileMapper extends BaseMapper<File> {

    @Update("""
            UPDATE files
               SET status = 'DELETED',
                   deleted_at = NOW(),
                   updated_at = NOW()
             WHERE user_id = #{userId}
               AND business_type = #{businessType}
               AND business_id = #{businessId}
               AND deleted_at IS NULL
            """)
    int softDeleteByBusiness(
            @Param("userId") String userId,
            @Param("businessType") String businessType,
            @Param("businessId") String businessId);
}
