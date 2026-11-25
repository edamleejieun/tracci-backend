package com.tracci.integration.tencentwsa;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class TencentWsaIntegrationConfiguration {

    @Bean
    public TencentWsaSearchClient tencentWsaSearchClient(
            TencentWsaProperties properties,
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.getTimeoutMs());
        requestFactory.setReadTimeout(properties.getTimeoutMs());
        return new TencentWsaSearchClientImpl(
                properties,
                restClientBuilder.requestFactory(requestFactory).build(),
                objectMapper);
    }
}
