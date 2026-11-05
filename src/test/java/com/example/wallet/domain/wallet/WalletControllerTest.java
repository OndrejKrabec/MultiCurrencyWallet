package com.example.wallet.domain.wallet;

import static org.hamcrest.Matchers.contains;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.wallet.common.money.Funds;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Currency;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(WalletController.class)
@Import(WalletMapper.class)
class WalletControllerTest {

    private static final Currency USD = Currency.getInstance("USD");
    private static final Currency EUR = Currency.getInstance("EUR");
    private static final Currency JPY = Currency.getInstance("JPY");

    @Autowired private MockMvc mvc;

    @MockitoBean private WalletService wallet;

    @Test
    void depositAcceptsAmountAsStringAndReturnsSortedBalancesWithMinorUnits() throws Exception {
        when(wallet.deposit(new Funds(USD, new BigDecimal("10.5"))))
                .thenReturn(
                        Map.of(
                                USD,
                                new BigDecimal("10.5"),
                                EUR,
                                new BigDecimal("5"),
                                JPY,
                                new BigDecimal("1000")));

        mvc.perform(
                        post("/wallet/deposits")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"currency\": \"USD\", \"amount\": \"10.5\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balances[*].currency").value(contains("EUR", "JPY", "USD")))
                .andExpect(
                        jsonPath("$.balances[*].amount").value(contains("5.00", "1000", "10.50")));
    }

    @Test
    void depositAlsoAcceptsJsonNumber() throws Exception {
        when(wallet.deposit(new Funds(USD, new BigDecimal("10.50"))))
                .thenReturn(Map.of(USD, new BigDecimal("10.50")));

        mvc.perform(
                        post("/wallet/deposits")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"currency\": \"USD\", \"amount\": 10.50}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balances[0].amount").value("10.50"));
    }

    @Test
    void withdrawReturnsRemainingBalances() throws Exception {
        when(wallet.withdraw(new Funds(USD, new BigDecimal("3"))))
                .thenReturn(Map.of(USD, new BigDecimal("7.00")));

        mvc.perform(
                        post("/wallet/withdrawals")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"currency\": \"USD\", \"amount\": \"3\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balances[0].amount").value("7.00"));
    }

    @Test
    void fullyWithdrawnCurrencyIsShownWithZero() throws Exception {
        when(wallet.withdraw(new Funds(USD, new BigDecimal("7"))))
                .thenReturn(Map.of(USD, BigDecimal.ZERO));

        mvc.perform(
                        post("/wallet/withdrawals")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"currency\": \"USD\", \"amount\": \"7\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balances[0].currency").value("USD"))
                .andExpect(jsonPath("$.balances[0].amount").value("0.00"));
    }

    @Test
    void totalShowsTheAmountAndTheFixingItWasCalculatedWith() throws Exception {
        var total = new Funds(EUR, new BigDecimal("11.02"));
        when(wallet.calculateTotal(EUR))
                .thenReturn(new WalletTotal(total, LocalDate.of(2026, 10, 30)));

        mvc.perform(get("/wallet/total").param("currency", "EUR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currency").value("EUR"))
                .andExpect(jsonPath("$.amount").value("11.02"))
                .andExpect(jsonPath("$.ratesValidFor").value("2026-10-30"));
    }
}
