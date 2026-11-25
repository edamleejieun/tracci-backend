package com.tracci.module.feedback.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan("com.tracci.module.feedback.repository")
public class FeedbackMybatisConfig {
}
