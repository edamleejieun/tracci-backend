package com.tracci.module.expense.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tracci.module.expense.entity.ExpenseCategoryEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ExpenseCategoryMapper extends BaseMapper<ExpenseCategoryEntity> {
}
