package com.tracci;

import org.mockito.Answers;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.mockito.Mockito.mock;

/**
 * 集成测试启动时替代真实 Redis（本仓库单元/冒烟测试不依赖外部 Redis）。
 */
@TestConfiguration
public class TestRedisStubConfiguration {

    @Bean
    @Primary
    public StringRedisTemplate stringRedisTemplate() {
        return mock(StringRedisTemplate.class, Answers.RETURNS_DEEP_STUBS);
    }
}
