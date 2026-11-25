package com.tracci.module.placecontent.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan("com.tracci.module.placecontent.repository")
public class PlaceContentMybatisConfig {
}
