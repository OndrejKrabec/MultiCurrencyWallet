package com.example.wallet.domain.wallet;

import com.example.wallet.common.exception.InsufficientFundsException;
import com.example.wallet.common.money.Funds;
import java.math.BigDecimal;
import java.util.Currency;
import java.util.Map;

/**
 * Balances per currency, safe for concurrent use. This is the storage boundary of the application:
 * {@link InMemoryWallet} today, a database tomorrow.
 *
 * <p>The operations are deliberately atomic commands rather than load/save, so an implementation
 * can never reintroduce the read-then-write race: in SQL a withdrawal is one conditional {@code
 * UPDATE balance SET amount = amount - ? WHERE currency = ? AND amount >= ?}.
 */
public interface Wallet {

    /** Adds a positive amount to the balance of its currency. */
    void deposit(Funds funds);

    /**
     * Withdraws a positive amount atomically: either the whole amount or nothing.
     *
     * @throws InsufficientFundsException if the balance of that currency is lower than the amount
     */
    void withdraw(Funds funds);

    /**
     * An immutable snapshot of the balances; a currency stays listed, with zero, after it is fully
     * withdrawn.
     */
    Map<Currency, BigDecimal> balances();
}
