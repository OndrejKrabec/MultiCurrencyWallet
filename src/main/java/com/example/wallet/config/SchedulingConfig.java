package com.example.wallet.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Runs {@code @Scheduled} methods (the CNB rate refresh). Off in the test profile, where tests
 * trigger refreshes themselves.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnBooleanProperty(name = "wallet.scheduling.enabled", matchIfMissing = true)
class SchedulingConfig {}
