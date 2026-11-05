package com.example.wallet.common.money;

import com.example.wallet.common.exception.InvalidAmountException;
import com.example.wallet.common.exception.UnsupportedCurrencyException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;

/**
 * An exact amount of money in one currency, always at the currency's ISO 4217 minor units (2 for
 * USD, 0 for JPY): {@code 7} and {@code 7.00} USD are the same {@code Funds}. Pseudo-currencies
 * without minor units (e.g. XDR, the IMF special drawing right) are not money a wallet can hold.
 */
public record Funds(Currency currency, BigDecimal amount) {

    /**
     * How a calculated amount is rounded to minor units, once, at the end of a calculation done in
     * full precision: down (towards zero), so a value is never shown higher than it is.
     */
    public static final RoundingMode ROUNDING_MODE = RoundingMode.DOWN;

    public Funds {
        validateNotPseudoCurrency(currency);
        int minorUnits = currency.getDefaultFractionDigits();
        if (amount.stripTrailingZeros().scale() > minorUnits) {
            throw new InvalidAmountException(
                    "%s amounts can have at most %d decimal places"
                            .formatted(currency.getCurrencyCode(), minorUnits));
        }
        // exact: the check above guarantees no digit is lost
        amount = amount.setScale(minorUnits);
    }

    /** An exact (e.g. converted) value rounded with {@link #ROUNDING_MODE} to minor units. */
    public static Funds rounded(Currency currency, BigDecimal exact) {
        return new Funds(
                currency, exact.setScale(currency.getDefaultFractionDigits(), ROUNDING_MODE));
    }

    /** E.g. {@code "7.00"} for USD or {@code "1000"} for JPY. */
    public String toPlainString() {
        return amount.toPlainString();
    }

    /** Java reports -1 decimal places for pseudo-currencies such as XDR (quoted by CNB) or XAU. */
    private static void validateNotPseudoCurrency(Currency currency) {
        if (currency.getDefaultFractionDigits() < 0) {
            throw new UnsupportedCurrencyException(currency.getCurrencyCode());
        }
    }
}
