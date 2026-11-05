package com.example.wallet.common.exception;

/** No exchange rates are available at all, so a conversion cannot be made. */
public final class RatesUnavailableException extends RuntimeException {

    public RatesUnavailableException(String message) {
        super(message);
    }
}
