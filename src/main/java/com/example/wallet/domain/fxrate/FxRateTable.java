package com.example.wallet.domain.fxrate;

import com.example.wallet.common.exception.UnsupportedCurrencyException;
import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalDate;
import java.util.Currency;
import java.util.Map;
import java.util.Optional;

/**
 * The exchange rates valid for a given day, all quoted against one {@code base} currency, and
 * conversions with them. Independent of where the rates come from (CNB quotes against CZK, another
 * source could use EUR).
 *
 * <p>The base is always supported with an implicit rate of 1, so callers never special-case it.
 * Other pairs are converted through the base, i.e. cross rates are calculated, not official.
 * Conversions keep full precision ({@link MathContext#DECIMAL128}); rounding is left to the caller.
 */
public record FxRateTable(Currency base, LocalDate validFor, Map<Currency, FxRate> rates) {

    private static final MathContext PRECISION = MathContext.DECIMAL128;

    public FxRateTable {
        rates = Map.copyOf(rates);
        if (rates.containsKey(base)) {
            throw new IllegalArgumentException(
                    "The base currency " + base + " must not be quoted against itself");
        }
    }

    public Optional<FxRate> findRate(Currency currency) {
        return base.equals(currency)
                ? Optional.of(new FxRate(base, 1, BigDecimal.ONE))
                : Optional.ofNullable(rates.get(currency));
    }

    /**
     * Guards that {@code currency} can be valued with this table.
     *
     * @throws UnsupportedCurrencyException if this table has no rate for it
     */
    public void validateSupported(Currency currency) {
        getRate(currency);
    }

    /**
     * The exact (unrounded) value of {@code amount} of {@code from} in {@code to}.
     *
     * @throws UnsupportedCurrencyException if the currencies differ and either is not in this table
     */
    public BigDecimal convert(BigDecimal amount, Currency from, Currency to) {
        if (from.equals(to)) {
            return amount;
        }
        FxRate source = getRate(from);
        FxRate target = getRate(to);
        // amount × rateFrom × unitsTo / (rateTo × unitsFrom): multiply first and divide once at
        // the end, so a round trip such as 21.042 CZK → USD (at 21.042) gives exactly 1, not 0.999…
        BigDecimal numerator =
                amount.multiply(source.rate()).multiply(BigDecimal.valueOf(target.units()));
        BigDecimal denominator = target.rate().multiply(BigDecimal.valueOf(source.units()));
        return numerator.divide(denominator, PRECISION);
    }

    /**
     * The rate of {@code currency}.
     *
     * @throws UnsupportedCurrencyException if this table has no rate for it
     */
    public FxRate getRate(Currency currency) {
        return findRate(currency)
                .orElseThrow(() -> new UnsupportedCurrencyException(currency.getCurrencyCode()));
    }
}
