package com.example.wallet.domain.wallet;

import com.example.wallet.common.exception.InvalidAmountException;
import com.example.wallet.common.exception.UnsupportedCurrencyException;
import com.example.wallet.common.money.Funds;
import com.example.wallet.domain.fxrate.FxRateProvider;
import java.math.BigDecimal;
import java.util.Currency;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Use cases of the single, implicit wallet.
 *
 * <ul>
 *   <li>Deposits accept the rate table's base and every currency quoted against it ("we don't
 *       accept money we can't value").
 *   <li>Withdrawals never need rates: any currency held can always be taken out.
 *   <li>Totals need rates and report which fixing was used.
 * </ul>
 *
 * Deposits and withdrawals return the balances read right after the change, which may already
 * include concurrent changes by other requests.
 */
@Service
public class WalletService {

    private final Wallet wallet;
    private final FxRateProvider rates;

    public WalletService(Wallet wallet, FxRateProvider rates) {
        this.wallet = wallet;
        this.rates = rates;
    }

    public Map<Currency, BigDecimal> deposit(Funds funds) {
        validatePositive(funds);
        if (!rates.getCurrentRates().hasRate(funds.currency())) {
            throw new UnsupportedCurrencyException(funds.currency().getCurrencyCode());
        }
        wallet.deposit(funds);
        return wallet.balances();
    }

    public Map<Currency, BigDecimal> withdraw(Funds funds) {
        validatePositive(funds);
        wallet.withdraw(funds);
        return wallet.balances();
    }

    public Map<Currency, BigDecimal> balances() {
        return wallet.balances();
    }

    public WalletTotal calculateTotal(Currency target) {
        return WalletTotal.of(wallet.balances(), rates.getCurrentRates(), target);
    }

    /**
     * Not a {@link Funds} rule: a total of zero is valid, a deposit or withdrawal of zero is not.
     */
    private static void validatePositive(Funds funds) {
        if (funds.amount().signum() <= 0) {
            throw new InvalidAmountException("Amount must be greater than 0");
        }
    }
}
