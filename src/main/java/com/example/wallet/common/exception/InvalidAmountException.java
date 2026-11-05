package com.example.wallet.common.exception;

/** The amount is not positive or has more decimal places than its currency allows. */
public final class InvalidAmountException extends RuntimeException {

    public InvalidAmountException(String message) {
        super(message);
    }
}
