package com.tracci.module.trip.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan("com.tracci.module.trip.repository")
public class TripMybatisConfig {
}
