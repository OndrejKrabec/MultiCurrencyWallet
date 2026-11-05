package com.example.wallet.common.cnb;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * The {@code cnb} component of {@code /actuator/health}: reads the rate cache, never calls CNB.
 * Never DOWN, because withdrawals and balances work without CNB, so a CNB outage must not make the
 * whole service look dead.
 */
@Component("cnb")
class CnbHealthIndicator implements HealthIndicator {

    private final CnbFxRateProvider provider;

    CnbHealthIndicator(CnbFxRateProvider provider) {
        this.provider = provider;
    }

    @Override
    public Health health() {
        return provider.findCurrentRates()
                .map(rates -> {
                    boolean hasLastRefreshFailed = provider.hasLastRefreshFailed();
                    return (hasLastRefreshFailed ? Health.unknown() : Health.up())
                            .withDetail("ratesValidFor", rates.validFor().toString())
                            .withDetail("hasLastRefreshFailed", hasLastRefreshFailed);
                })
                .orElseGet(() -> Health.unknown().withDetail("rates", "not loaded yet"))
                .build();
    }
}
