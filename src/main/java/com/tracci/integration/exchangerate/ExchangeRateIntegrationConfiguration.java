package com.tracci.integration.exchangerate;

import com.tracci.common.exception.ThirdPartyException;
import com.tracci.common.response.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class ExchangeRateIntegrationConfiguration {

    @Bean
    public ExchangeRateClient exchangeRateClient(
            @Value("${tracci.integration.exchange-rate.mock:true}") boolean mock) {
        if (mock) {
            return new MockExchangeRateClient();
        }
        log.error("生产环境汇率服务尚未接入");
        return currency -> {
            throw new ThirdPartyException(ErrorCode.EXCHANGE_RATE_UNAVAILABLE);
        };
    }
}
