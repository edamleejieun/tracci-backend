package com.tracci.module.feedback.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tracci.module.feedback.entity.Feedback;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface FeedbackMapper extends BaseMapper<Feedback> {
}
