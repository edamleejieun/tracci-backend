package com.tracci.integration.amap;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
public class AmapIntegrationConfiguration {

    @Bean
    public AmapPoiClient amapPoiClient(
            AmapProperties properties,
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper) {
        int timeoutMs = Math.max(properties.getPoi().getTimeoutMs(), 1000);
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(timeoutMs));
        requestFactory.setReadTimeout(Duration.ofMillis(timeoutMs));
        return new AmapPoiClientImpl(properties, restClientBuilder.requestFactory(requestFactory), objectMapper);
    }

    @Bean
    public AmapWeatherClient amapWeatherClient(
            AmapProperties properties,
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper) {
        int timeoutMs = Math.max(properties.getWeather().getTimeoutMs(), 1000);
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(timeoutMs));
        requestFactory.setReadTimeout(Duration.ofMillis(timeoutMs));
        return new AmapWeatherClientImpl(properties, restClientBuilder.requestFactory(requestFactory), objectMapper);
    }
}
