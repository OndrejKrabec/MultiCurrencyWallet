package com.example.wallet.domain.fxrate;

import com.example.wallet.domain.fxrate.dto.ExchangeRatesResponseDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/exchange-rates", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Exchange rates", description = "The Czech National Bank exchange rates in use")
@ApiResponse(
        responseCode = "500",
        description = "Unexpected error; details are only logged",
        content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE))
public class ExchangeRatesController {

    private final FxRateProvider fxRateProvider;
    private final ExchangeRatesMapper exchangeRatesMapper;

    public ExchangeRatesController(
            FxRateProvider fxRateProvider, ExchangeRatesMapper exchangeRatesMapper) {
        this.fxRateProvider = fxRateProvider;
        this.exchangeRatesMapper = exchangeRatesMapper;
    }

    @Operation(
            summary = "Returns the CNB rates in use.",
            description =
                    "The latest CNB fixing loaded (CNB publishes one per working day at about 14:30 Prague time). "
                            + "Also the list of supported currencies (plus CZK).")
    @ApiResponse(responseCode = "200", description = "The fixing in use")
    @ApiResponse(
            responseCode = "503",
            description = "No valid CNB rates have been loaded yet",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE))
    @GetMapping
    public ExchangeRatesResponseDto exchangeRates() {
        return exchangeRatesMapper.toExchangeRatesResponseDto(fxRateProvider.getCurrentRates());
    }
}
