package com.example.wallet.domain.wallet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.wallet.common.exception.InvalidAmountException;
import com.example.wallet.common.exception.RatesUnavailableException;
import com.example.wallet.common.exception.UnsupportedCurrencyException;
import com.example.wallet.common.money.Funds;
import com.example.wallet.domain.fxrate.FxRate;
import com.example.wallet.domain.fxrate.FxRateProvider;
import com.example.wallet.domain.fxrate.FxRateTable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Currency;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WalletServiceTest {

    private static final Currency CZK = Currency.getInstance("CZK");
    private static final Currency USD = Currency.getInstance("USD");
    private static final Currency EUR = Currency.getInstance("EUR");
    private static final Currency GBP = Currency.getInstance("GBP");

    private final FxRateProvider rates = mock(FxRateProvider.class);
    private final WalletService service = new WalletService(new InMemoryWallet(), rates);

    private final FxRateTable table =
            new FxRateTable(
                    CZK,
                    LocalDate.of(2026, 10, 30),
                    Map.of(
                            USD, new FxRate(USD, 1, new BigDecimal("20")),
                            EUR, new FxRate(EUR, 1, new BigDecimal("25"))));

    @Test
    void depositsAndTotalsUsingCurrentRates() {
        when(rates.getCurrentRates()).thenReturn(table);
        service.deposit(funds(USD, "10"));
        service.deposit(funds(EUR, "4"));

        WalletTotal total = service.calculateTotal(CZK);

        assertThat(total.total()).isEqualTo(funds(CZK, "300.00"));
        assertThat(total.ratesValidFor()).isEqualTo(LocalDate.of(2026, 10, 30));
    }

    @Test
    void depositOfCurrencyNotQuotedByCnbIsRejected() {
        when(rates.getCurrentRates()).thenReturn(table);

        assertThatThrownBy(() -> service.deposit(funds(GBP, "1")))
                .isInstanceOf(UnsupportedCurrencyException.class);
        assertThat(service.balances()).isEmpty();
    }

    @Test
    void nonPositiveAmountsAreRejected() {
        assertThatThrownBy(() -> service.deposit(funds(CZK, "0")))
                .isInstanceOf(InvalidAmountException.class);
        assertThatThrownBy(() -> service.withdraw(funds(CZK, "-1")))
                .isInstanceOf(InvalidAmountException.class);
        verifyNoInteractions(rates);
    }

    @Test
    void depositNeedsRatesEvenInTheBaseCurrency() {
        when(rates.getCurrentRates()).thenThrow(new RatesUnavailableException("down"));

        assertThatThrownBy(() -> service.deposit(funds(CZK, "100")))
                .isInstanceOf(RatesUnavailableException.class);
        assertThat(service.balances()).isEmpty();
    }

    @Test
    void heldForeignCurrencyCanBeWithdrawnWithoutCnb() {
        when(rates.getCurrentRates()).thenReturn(table);
        service.deposit(funds(USD, "10"));
        when(rates.getCurrentRates()).thenThrow(new RatesUnavailableException("down"));

        service.withdraw(funds(USD, "4"));

        assertThat(service.balances().get(USD)).isEqualByComparingTo("6");
    }

    private static Funds funds(Currency currency, String amount) {
        return new Funds(currency, new BigDecimal(amount));
    }
}
