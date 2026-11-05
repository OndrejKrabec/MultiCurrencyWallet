package com.example.wallet.domain.wallet;

import com.example.wallet.common.exception.InsufficientFundsException;
import com.example.wallet.common.money.Funds;
import java.math.BigDecimal;
import java.util.Currency;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Component;

/**
 * A {@link Wallet} kept in a {@link ConcurrentHashMap}.
 *
 * <p>A withdrawal checks the balance and subtracts in one atomic {@link ConcurrentMap#compute} call
 * per currency, so concurrent withdrawals can never overdraw. If the remapping function throws, the
 * balance is left unchanged (guaranteed by {@code ConcurrentHashMap#compute}).
 *
 * <p>No operation touches two currencies at once, so a {@link #balances()} snapshot is consistent
 * per currency, which is all a total needs.
 */
@Component
final class InMemoryWallet implements Wallet {

    private final ConcurrentMap<Currency, BigDecimal> balances = new ConcurrentHashMap<>();

    @Override
    public void deposit(Funds funds) {
        balances.merge(funds.currency(), funds.amount(), BigDecimal::add);
    }

    @Override
    public void withdraw(Funds funds) {
        balances.compute(
                funds.currency(),
                (currency, current) -> {
                    BigDecimal available = current == null ? BigDecimal.ZERO : current;
                    if (available.compareTo(funds.amount()) < 0) {
                        throw new InsufficientFundsException(funds, new Funds(currency, available));
                    }
                    return available.subtract(funds.amount());
                });
    }

    @Override
    public Map<Currency, BigDecimal> balances() {
        return Map.copyOf(balances);
    }
}
