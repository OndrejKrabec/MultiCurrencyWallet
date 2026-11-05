package com.example.wallet.domain.wallet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.wallet.common.exception.UnsupportedCurrencyException;
import com.example.wallet.common.money.Funds;
import com.example.wallet.domain.fxrate.FxRate;
import com.example.wallet.domain.fxrate.FxRateTable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Currency;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WalletTotalTest {

    private static final Currency CZK = Currency.getInstance("CZK");
    private static final Currency USD = Currency.getInstance("USD");
    private static final Currency EUR = Currency.getInstance("EUR");
    private static final Currency GBP = Currency.getInstance("GBP");
    private static final Currency JPY = Currency.getInstance("JPY");
    private static final Currency HUF = Currency.getInstance("HUF");
    private static final Currency IDR = Currency.getInstance("IDR");

    /** Illustrative rates in CNB format: per 100 JPY/HUF, per 1000 IDR. */
    private final FxRateTable cnb =
            table(
                    rate(USD, 1, "21.042"),
                    rate(EUR, 1, "24.335"),
                    rate(JPY, 100, "13.875"),
                    rate(HUF, 100, "6.649"),
                    rate(IDR, 1000, "1.204"));

    @Test
    void assignmentExampleTotalInUsd() {
        // Assignment example: 7 USD + 5 EUR at 1 EUR = 1.16 USD → 12.80 USD
        FxRateTable rates = table(rate(USD, 1, "1"), rate(EUR, 1, "1.16"));

        assertThat(total(rates, balances(USD, "7", EUR, "5"), USD)).isEqualTo(funds(USD, "12.80"));
    }

    @Test
    void assignmentExampleTotalInEur() {
        // Assignment example: 5 EUR + 7 USD at 1 USD = 0.86 EUR → 11.02 EUR.
        // The PDF's two rates are rounded and not exact inverses (1.16 × 0.86 ≠ 1), so each case
        // gets its own table.
        FxRateTable rates = table(rate(EUR, 1, "1"), rate(USD, 1, "0.86"));

        assertThat(total(rates, balances(USD, "7", EUR, "5"), EUR)).isEqualTo(funds(EUR, "11.02"));
    }

    @Test
    void roundsTheSumOnceNotEachBalance() {
        // each balance is worth 0.335 CZK: rounding each down would give 0.99, rounding the
        // exact sum 1.005 once gives 1.00
        FxRateTable rates =
                table(rate(USD, 1, "0.335"), rate(EUR, 1, "0.335"), rate(GBP, 1, "0.335"));

        assertThat(total(rates, balances(USD, "1", EUR, "1", GBP, "1"), CZK))
                .isEqualTo(funds(CZK, "1.00"));
    }

    @Test
    void reportsTheDateOfTheFixingUsed() {
        assertThat(WalletTotal.of(Map.of(), cnb, EUR).ratesValidFor())
                .isEqualTo(LocalDate.of(2026, 11, 3));
    }

    @Test
    void emptyWalletIsWorthZero() {
        assertThat(total(cnb, Map.of(), EUR)).isEqualTo(funds(EUR, "0.00"));
    }

    @Test
    void zeroBalanceNeedsNoRate() {
        // GBP isn't in this table, e.g. because CNB stopped quoting it after it was fully withdrawn
        assertThat(total(cnb, balances(USD, "10", GBP, "0.00"), CZK))
                .isEqualTo(funds(CZK, "210.42"));
    }

    @Test
    void unsupportedTargetOrBalanceCurrencyIsRejected() {
        assertThatThrownBy(() -> total(cnb, balances(USD, "1"), GBP))
                .isInstanceOf(UnsupportedCurrencyException.class)
                .hasMessageContaining("GBP");
        assertThatThrownBy(() -> total(cnb, balances(GBP, "1"), USD))
                .isInstanceOf(UnsupportedCurrencyException.class);
    }

    private static Funds total(
            FxRateTable rates, Map<Currency, BigDecimal> balances, Currency target) {
        return WalletTotal.of(balances, rates, target).total();
    }

    private static Funds funds(Currency currency, String amount) {
        return new Funds(currency, new BigDecimal(amount));
    }

    private static Map<Currency, BigDecimal> balances(Object... currencyAmountPairs) {
        var balances = new LinkedHashMap<Currency, BigDecimal>();
        for (int i = 0; i < currencyAmountPairs.length; i += 2) {
            balances.put(
                    (Currency) currencyAmountPairs[i],
                    new BigDecimal((String) currencyAmountPairs[i + 1]));
        }
        return balances;
    }

    private static FxRate rate(Currency currency, int units, String rate) {
        return new FxRate(currency, units, new BigDecimal(rate));
    }

    private static FxRateTable table(FxRate... rates) {
        var map = new LinkedHashMap<Currency, FxRate>();
        for (FxRate rate : rates) {
            map.put(rate.currency(), rate);
        }
        return new FxRateTable(CZK, LocalDate.of(2026, 11, 3), map);
    }
}
