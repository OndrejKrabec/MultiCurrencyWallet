package com.example.wallet.common.money;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.wallet.common.exception.InvalidAmountException;
import com.example.wallet.common.exception.UnsupportedCurrencyException;
import java.math.BigDecimal;
import java.util.Currency;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class FundsTest {

    private static final Currency USD = Currency.getInstance("USD");

    @ParameterizedTest
    @CsvSource({"USD, 10", "USD, 10.5", "USD, 10.55", "USD, 10.500", "JPY, 1000", "JPY, 1000.00"})
    void acceptsAmountsWithinMinorUnits(String currency, String amount) {
        assertThat(amount(currency, amount).amount()).isEqualByComparingTo(amount);
    }

    @ParameterizedTest
    @CsvSource({"USD, 10.555", "JPY, 1.5"})
    void rejectsMoreDecimalsThanTheCurrencyHas(String currency, String amount) {
        assertThatThrownBy(() -> amount(currency, amount))
                .isInstanceOf(InvalidAmountException.class)
                .hasMessageContaining(currency);
    }

    @Test
    void rejectsPseudoCurrenciesWithoutMinorUnits() {
        // CNB quotes XDR (the IMF special drawing right), but it is not money a wallet can hold
        assertThatThrownBy(() -> amount("XDR", "1"))
                .isInstanceOf(UnsupportedCurrencyException.class);
    }

    @ParameterizedTest
    @CsvSource({"USD, 7, 7.00", "USD, 10.500, 10.50", "JPY, 1E+3, 1000"})
    void plainStringHasExactlyTheMinorUnits(String currency, String amount, String expected) {
        assertThat(amount(currency, amount).toPlainString()).isEqualTo(expected);
    }

    @Test
    void roundedRoundsDownNeverUp() {
        assertThat(Funds.rounded(USD, new BigDecimal("0.125")).toPlainString()).isEqualTo("0.12");
        assertThat(Funds.rounded(USD, new BigDecimal("0.1299")).toPlainString()).isEqualTo("0.12");
        assertThat(
                        Funds.rounded(Currency.getInstance("JPY"), new BigDecimal("1516.99"))
                                .toPlainString())
                .isEqualTo("1516");
    }

    @Test
    void sameValueIsEqualWhateverTheScale() {
        var seven = amount("USD", "7");
        var sevenWithCents = amount("USD", "7.00");

        assertThat(seven).isEqualTo(sevenWithCents).hasSameHashCodeAs(sevenWithCents);
        assertThat(amount("JPY", "1E+3")).isEqualTo(amount("JPY", "1000"));
    }

    private static Funds amount(String currency, String amount) {
        return new Funds(Currency.getInstance(currency), new BigDecimal(amount));
    }
}
