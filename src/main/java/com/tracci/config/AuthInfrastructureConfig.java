package com.tracci.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({
        JwtProperties.class,
        AuthWebProperties.class,
        CorsProperties.class,
        TestAccountProperties.class,
        AuthFeatureProperties.class,
        RequestLoggingProperties.class,
        AdminProperties.class
})
public class AuthInfrastructureConfig {
}
