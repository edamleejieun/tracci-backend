package com.tracci.module.expense.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties("tracci.expense")
public class ExpenseProperties {

    /**
     * MVP only supports CNY. Enable this later when real multi-currency exchange is ready.
     */
    private boolean multiCurrencyEnabled = false;
}
