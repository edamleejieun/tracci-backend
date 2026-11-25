package com.tracci.module.file.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan("com.tracci.module.file.repository")
public class FileMybatisConfig {
}
