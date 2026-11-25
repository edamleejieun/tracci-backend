package com.tracci.integration.exchangerate;

import com.tracci.common.exception.BizException;
import com.tracci.common.response.ErrorCode;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 开发环境汇率 Mock，提供固定汇率快照
 */
@Slf4j
public class MockExchangeRateClient implements ExchangeRateClient {

    private static final Map<String, String> RATES = Map.ofEntries(
            Map.entry("CNY", "1.00000000"),
            Map.entry("JPY", "0.04830000"),
            Map.entry("USD", "7.24000000"),
            Map.entry("EUR", "7.90000000"),
            Map.entry("KRW", "0.00530000"),
            Map.entry("THB", "0.19800000"),
            Map.entry("SGD", "5.38000000"),
            Map.entry("GBP", "9.16000000"),
            Map.entry("HKD", "0.93000000"),
            Map.entry("AUD", "4.71000000"),
            Map.entry("MYR", "1.56000000")
    );

    @Override
    public BigDecimal getRate(String currency) {
        String rate = RATES.get(currency.toUpperCase());
        if (rate == null) {
            log.warn("[MockExchangeRate] unsupported currency: {}", currency);
            throw new BizException(ErrorCode.AUTH_PARAM_INVALID, "不支持的币种");
        }
        log.debug("[MockExchangeRate] currency={}, rate={}", currency, rate);
        return new BigDecimal(rate);
    }
}
