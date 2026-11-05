package com.example.wallet.domain.fxrate;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Currency;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ExchangeRatesController.class)
@Import(ExchangeRatesMapper.class)
class ExchangeRatesControllerTest {

    private static final Currency CZK = Currency.getInstance("CZK");

    private static final Currency USD = Currency.getInstance("USD");
    private static final Currency JPY = Currency.getInstance("JPY");

    @Autowired private MockMvc mvc;

    @MockitoBean private FxRateProvider rates;

    @Test
    void listsCnbQuotesWithUnitsSortedByCurrency() throws Exception {
        var table =
                new FxRateTable(
                        CZK,
                        LocalDate.of(2026, 11, 3),
                        Map.of(
                                USD, new FxRate(USD, 1, new BigDecimal("21.762")),
                                JPY, new FxRate(JPY, 100, new BigDecimal("13.755"))));
        when(rates.getCurrentRates()).thenReturn(table);

        mvc.perform(get("/exchange-rates"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ratesValidFor").value("2026-11-03"))
                .andExpect(jsonPath("$.baseCurrency").value("CZK"))
                .andExpect(jsonPath("$.rates[0].currency").value("JPY"))
                .andExpect(jsonPath("$.rates[0].units").value(100))
                .andExpect(jsonPath("$.rates[0].rate").value("13.755"))
                .andExpect(jsonPath("$.rates[1].currency").value("USD"));
    }
}
