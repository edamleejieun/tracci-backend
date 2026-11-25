package com.tracci.integration.exchangerate;

import java.math.BigDecimal;

/**
 * 汇率查询适配，返回 1 单位目标币种 = N 人民币
 */
public interface ExchangeRateClient {

    BigDecimal getRate(String currency);
}
