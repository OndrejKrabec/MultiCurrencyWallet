package com.example.wallet.common.exception;

import com.example.wallet.common.money.Funds;

/** A withdrawal asked for more than the balance of that currency. */
public final class InsufficientFundsException extends RuntimeException {

    public InsufficientFundsException(Funds requested, Funds available) {
        super(
                "Cannot withdraw %s %s, available balance is %s %s"
                        .formatted(
                                requested.toPlainString(), requested.currency().getCurrencyCode(),
                                available.toPlainString(), available.currency().getCurrencyCode()));
    }
}
