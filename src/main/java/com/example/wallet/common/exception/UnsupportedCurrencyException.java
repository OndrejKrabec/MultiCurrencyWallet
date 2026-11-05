package com.example.wallet.common.exception;

/**
 * The currency is one we can't value: not in the current rate table, or a pseudo-currency such as
 * XDR.
 */
public final class UnsupportedCurrencyException extends RuntimeException {

    public UnsupportedCurrencyException(String currencyCode) {
        super("Currency %s is not supported".formatted(currencyCode));
    }
}
