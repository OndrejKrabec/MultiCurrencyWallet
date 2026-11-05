package com.example.wallet.common.exception;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.wallet.common.money.Funds;
import com.example.wallet.domain.fxrate.ExchangeRatesController;
import com.example.wallet.domain.fxrate.ExchangeRatesMapper;
import com.example.wallet.domain.fxrate.FxRateProvider;
import com.example.wallet.domain.wallet.WalletController;
import com.example.wallet.domain.wallet.WalletMapper;
import com.example.wallet.domain.wallet.WalletService;
import java.math.BigDecimal;
import java.util.Currency;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@WebMvcTest({WalletController.class, ExchangeRatesController.class})
@Import({WalletMapper.class, ExchangeRatesMapper.class})
class ApiErrorsTest {

    @Autowired private MockMvc mvc;

    @MockitoBean private WalletService wallet;

    @MockitoBean private FxRateProvider rates;

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource(
            delimiter = '|',
            textBlock =
                    """
                    {"amount": "1"}                                   | currency: must not be null
                    {"currency": "USD"}                               | amount: must not be null
                    {"currency": "USD", "amount": "0"}                | amount: must be greater than 0
                    {"currency": "USD", "amount": "-5"}               | amount: must be greater than 0
                    {"currency": "USD", "amount": "1e1000000"}        | amount: must have at most 15 integer and 15 decimal digits
                    {"currency": "USD", "amount": "1234567890123456"} | amount: must have at most 15 integer and 15 decimal digits
                    """)
    void invalidFieldIsA400ListingIt(String body, String error) throws Exception {
        postDeposit(body)
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Bad Request"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value("Invalid request content."))
                .andExpect(jsonPath("$.instance").value("/wallet/deposits"))
                .andExpect(jsonPath("$.errors").value(contains(error)));
    }

    @Test
    void everyInvalidFieldIsListed() throws Exception {
        postDeposit("{\"amount\": \"-1\"}")
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.errors")
                                .value(
                                        contains(
                                                "amount: must be greater than 0",
                                                "currency: must not be null")));
    }

    /** Jackson rejects these before validation, but knows the field. */
    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource(
            delimiter = '|',
            textBlock =
                    """
                    {"currency": "XYZ", "amount": "100"}             | currency: invalid value
                    {"currency": "usd", "amount": "1"}               | currency: invalid value
                    {"currency": "USD", "amount": "abc"}             | amount: invalid value
                    """)
    void unreadableFieldIsA400NamingIt(String body, String error) throws Exception {
        postDeposit(body)
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Invalid request content."))
                .andExpect(jsonPath("$.instance").value("/wallet/deposits"))
                .andExpect(jsonPath("$.errors").value(contains(error)));
    }

    /** No field to name: Spring's own 400, without the parser's text. */
    @ParameterizedTest
    @ValueSource(strings = {"{\"currency\": ", "", "[1, 2]"})
    void unreadableBodyIsA400(String body) throws Exception {
        postDeposit(body)
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Failed to read request"))
                .andExpect(jsonPath("$.errors").doesNotExist())
                .andExpect(content().string(not(containsString("jackson"))));
    }

    @Test
    void tooManyDecimalsForTheCurrencyIsA400() throws Exception {
        postDeposit("{\"currency\": \"JPY\", \"amount\": \"1.5\"}")
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.detail")
                                .value("JPY amounts can have at most 0 decimal places"));
    }

    @Test
    void missingTotalCurrencyParameterIsA400() throws Exception {
        mvc.perform(get("/wallet/total"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(
                        jsonPath("$.detail")
                                .value("Required parameter 'currency' is not present."));
    }

    @Test
    void codeThatIsNoCurrencyIsA400NotA422() throws Exception {
        mvc.perform(get("/wallet/total").param("currency", "XYZ"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value(containsString("'currency'")));
    }

    @Test
    void insufficientFundsIsA409() throws Exception {
        var usd = Currency.getInstance("USD");
        when(wallet.withdraw(any()))
                .thenThrow(
                        new InsufficientFundsException(
                                new Funds(usd, new BigDecimal("8")),
                                new Funds(usd, new BigDecimal("7"))));

        mvc.perform(
                        post("/wallet/withdrawals")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"currency\": \"USD\", \"amount\": \"8\"}"))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Conflict"))
                .andExpect(
                        jsonPath("$.detail")
                                .value("Cannot withdraw 8.00 USD, available balance is 7.00 USD"));
    }

    @Test
    void realCurrencyCnbDoesNotQuoteIsA422() throws Exception {
        when(wallet.calculateTotal(Currency.getInstance("KES")))
                .thenThrow(new UnsupportedCurrencyException("KES"));

        mvc.perform(get("/wallet/total").param("currency", "KES"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value("Currency KES is not supported"));
    }

    @Test
    void pseudoCurrencyIsA422() throws Exception {
        postDeposit("{\"currency\": \"XDR\", \"amount\": \"1\"}")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value("Currency XDR is not supported"));
    }

    @Test
    void noRatesIsA503() throws Exception {
        when(wallet.calculateTotal(Currency.getInstance("EUR")))
                .thenThrow(new RatesUnavailableException("CNB could not be reached"));

        mvc.perform(get("/wallet/total").param("currency", "EUR"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.detail").value("CNB could not be reached"));
    }

    @Test
    void unexpectedErrorRevealsNothing() throws Exception {
        when(wallet.balances()).thenThrow(new IllegalStateException("secret internal detail"));

        mvc.perform(get("/wallet"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.detail").value("Unexpected error"))
                .andExpect(content().string(not(containsString("secret"))))
                .andExpect(content().string(not(containsString("IllegalStateException"))));
    }

    private ResultActions postDeposit(String body) throws Exception {
        return mvc.perform(
                post("/wallet/deposits").contentType(MediaType.APPLICATION_JSON).content(body));
    }
}
