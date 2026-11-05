package com.example.wallet.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class ClockConfig {

    /**
     * The time source for code that needs "now" (the CNB rate cache); tests replace it to control
     * time.
     */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
