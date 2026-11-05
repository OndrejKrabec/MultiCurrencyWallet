package com.example.wallet.domain.fxrate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.wallet.common.exception.UnsupportedCurrencyException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Currency;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FxRateTableTest {

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
    void appliesUnitCountForMultiUnitQuotes() {
        assertThat(cnb.convert(amount("1000"), JPY, CZK)).isEqualByComparingTo("138.75");
        assertThat(cnb.convert(amount("138.75"), CZK, JPY)).isEqualByComparingTo("1000");
        assertThat(cnb.convert(amount("1000000"), IDR, CZK)).isEqualByComparingTo("1204");
    }

    @Test
    void convertsBetweenTwoMultiUnitCurrenciesThroughTheBase() {
        // 1000 JPY = 138.75 CZK; 1 HUF = 0.06649 CZK → 138.75 / 0.06649 = 2086.7799… HUF
        assertThat(cnb.convert(amount("1000"), JPY, HUF))
                .isBetween(amount("2086.77"), amount("2086.78"));
    }

    @Test
    void crossConversionDividesOnlyOnceAtTheEnd() {
        // 24.335 USD × 21.042 / 24.335 is exactly 21.042 EUR; dividing first (21.042 / 24.335 =
        // 0.8646…) and multiplying afterwards would give 21.04199…
        assertThat(cnb.convert(amount("24.335"), USD, EUR)).isEqualByComparingTo("21.042");
    }

    @Test
    void czkIsTheBaseWithRateOne() {
        assertThat(cnb.convert(amount("21.042"), CZK, USD)).isEqualByComparingTo("1");
        assertThat(cnb.convert(amount("1"), USD, CZK)).isEqualByComparingTo("21.042");
        assertThat(cnb.convert(amount("5"), CZK, CZK)).isEqualByComparingTo("5");
    }

    @Test
    void worksWithAnyBaseCurrency() {
        // e.g. a provider quoting against EUR instead of CNB's CZK: 1 USD = 0.9 EUR,
        // 100 JPY = 0.6 EUR
        FxRateTable eurBased =
                new FxRateTable(
                        EUR,
                        LocalDate.of(2026, 11, 3),
                        Map.of(USD, rate(USD, 1, "0.9"), JPY, rate(JPY, 100, "0.6")));

        assertThat(eurBased.hasRate(EUR)).isTrue();
        assertThat(eurBased.hasRate(CZK)).isFalse();
        assertThat(eurBased.convert(amount("10"), USD, EUR)).isEqualByComparingTo("9");
        assertThat(eurBased.convert(amount("1000"), JPY, EUR)).isEqualByComparingTo("6");
    }

    @Test
    void findRateIsEmptyAndGetRateThrowsForAnUnquotedCurrency() {
        assertThat(cnb.findRate(GBP)).isEmpty();
        assertThatThrownBy(() -> cnb.getRate(GBP))
                .isInstanceOf(UnsupportedCurrencyException.class)
                .hasMessageContaining("GBP");
    }

    @Test
    void rateAndUnitsMustBePositive() {
        assertThatThrownBy(() -> new FxRate(USD, 1, BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FxRate(USD, 0, BigDecimal.ONE))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void baseCannotBeQuotedAgainstItself() {
        assertThatThrownBy(() -> table(rate(CZK, 1, "1")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static BigDecimal amount(String amount) {
        return new BigDecimal(amount);
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
