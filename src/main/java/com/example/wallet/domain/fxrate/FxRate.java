package com.example.wallet.domain.fxrate;

import java.math.BigDecimal;
import java.util.Currency;

/**
 * {@code units} of {@code currency} are worth {@code rate} of the rate table's base currency,
 * exactly as quoted (e.g. 100 JPY = 13.755 CZK). The unit count is kept rather than divided out, so
 * no precision is lost before a conversion.
 */
public record FxRate(Currency currency, int units, BigDecimal rate) {

    public FxRate {
        if (units <= 0) {
            throw new IllegalArgumentException("units must be positive: " + units);
        }
        if (rate.signum() <= 0) {
            throw new IllegalArgumentException("rate must be positive");
        }
    }
}
