package com.tracci.module.savedplace.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan("com.tracci.module.savedplace.repository")
public class SavedPlaceMybatisConfig {
}
