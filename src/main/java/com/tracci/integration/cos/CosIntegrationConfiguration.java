package com.tracci.integration.cos;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Slf4j
@Configuration
public class CosIntegrationConfiguration {

    @Bean
    @ConditionalOnProperty(value = "tracci.integration.cos.enabled", havingValue = "true", matchIfMissing = false)
    public CosClient cosClient(CosProperties properties) {
        if (properties.isMock()) {
            log.info("COS client running in mock mode");
            return new MockCosClient(properties.getBucket(), properties.getRegion());
        }
        validateRealCosProperties(properties);
        log.info("COS client initialized for bucket={} region={}", properties.getBucket(), properties.getRegion());
        return new CosClientImpl(properties);
    }

    private void validateRealCosProperties(CosProperties properties) {
        if (!StringUtils.hasText(properties.getSecretId()) || !StringUtils.hasText(properties.getSecretKey())) {
            throw new IllegalStateException("Tencent COS secret-id/secret-key must be configured when mock=false");
        }
        if (!StringUtils.hasText(properties.getRegion())) {
            throw new IllegalStateException("Tencent COS region must be configured when mock=false");
        }
        if (!StringUtils.hasText(properties.getBucket()) || !properties.getBucket().matches(".+-\\d{5,}$")) {
            throw new IllegalStateException("Tencent COS bucket must use <bucketname>-<appid> format, for example tracci-1250000000");
        }
    }
}
