package com.tracci.module.expense.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan("com.tracci.module.expense.repository")
public class ExpenseMybatisConfig {
}
