package com.tracci.module.place.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan("com.tracci.module.place.repository")
public class PlaceMybatisConfig {
}
