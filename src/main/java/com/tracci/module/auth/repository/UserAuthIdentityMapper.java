package com.tracci.module.auth.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tracci.module.auth.entity.UserAuthIdentity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserAuthIdentityMapper extends BaseMapper<UserAuthIdentity> {
}
