package com.tracci.module.savedplace.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tracci.module.savedplace.entity.SavedPlace;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SavedPlaceMapper extends BaseMapper<SavedPlace> {
}
