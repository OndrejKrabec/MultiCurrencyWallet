package com.example.wallet.domain.wallet;

import com.example.wallet.common.exception.UnsupportedCurrencyException;
import com.example.wallet.common.money.Funds;
import com.example.wallet.domain.fxrate.FxRateTable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Currency;
import java.util.Map;

/**
 * All balances converted to one currency, and the date of the fixing used.
 *
 * @param total the sum, rounded once to the currency's minor units
 * @param ratesValidFor the date of the fixing the balances were converted with
 */
public record WalletTotal(Funds total, LocalDate ratesValidFor) {

    /**
     * Converts every balance to {@code target}, sums the exact amounts and rounds the sum once.
     *
     * @throws UnsupportedCurrencyException if the target or a non-zero balance's currency has no
     *     rate
     */
    public static WalletTotal of(
            Map<Currency, BigDecimal> balances, FxRateTable rates, Currency target) {
        rates.validateSupported(target);
        BigDecimal exact =
                balances.entrySet().stream()
                        // a zero balance needs no rate (e.g. a currency CNB no longer quotes)
                        .filter(balance -> balance.getValue().signum() != 0)
                        .map(balance -> rates.convert(balance.getValue(), balance.getKey(), target))
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new WalletTotal(Funds.rounded(target, exact), rates.validFor());
    }
}
