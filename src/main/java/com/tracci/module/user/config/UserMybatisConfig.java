package com.tracci.module.user.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan("com.tracci.module.user.repository")
public class UserMybatisConfig {
}
