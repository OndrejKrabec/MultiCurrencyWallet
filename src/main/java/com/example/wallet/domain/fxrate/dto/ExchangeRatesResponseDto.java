package com.example.wallet.domain.fxrate.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import java.time.LocalDate;
import java.util.Currency;
import java.util.List;

@Schema(name = "ExchangeRatesResponse", description = "The CNB fixing in use")
public record ExchangeRatesResponseDto(
        @Schema(
                        description = "Date of the CNB fixing",
                        requiredMode = RequiredMode.REQUIRED,
                        example = "2026-11-03")
                LocalDate ratesValidFor,
        @Schema(
                        type = "string",
                        description = "Base currency of all rates",
                        requiredMode = RequiredMode.REQUIRED,
                        example = "CZK")
                Currency baseCurrency,
        @Schema(
                        description =
                                "Quotes sorted by currency code; the base itself is implicit (rate 1)",
                        requiredMode = RequiredMode.REQUIRED)
                List<ExchangeRateDto> rates) {

    public ExchangeRatesResponseDto {
        rates = List.copyOf(rates);
    }
}
