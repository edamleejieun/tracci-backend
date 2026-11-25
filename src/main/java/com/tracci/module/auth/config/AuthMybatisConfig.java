package com.tracci.module.auth.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan("com.tracci.module.auth.repository")
public class AuthMybatisConfig {
}
