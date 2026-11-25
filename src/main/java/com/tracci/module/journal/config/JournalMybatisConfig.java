package com.tracci.module.journal.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan("com.tracci.module.journal.repository")
public class JournalMybatisConfig {
}
