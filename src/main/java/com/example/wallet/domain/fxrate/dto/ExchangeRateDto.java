package com.example.wallet.domain.fxrate.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import java.util.Currency;

@Schema(
        name = "ExchangeRate",
        description =
                "One CNB quote: `units` of the currency are worth `rate` in the base currency")
public record ExchangeRateDto(
        @Schema(
                        type = "string",
                        description = "ISO 4217 currency code",
                        requiredMode = RequiredMode.REQUIRED,
                        example = "JPY")
                Currency currency,
        @Schema(
                        description = "Number of units the rate is quoted for (1, 100 or 1000)",
                        requiredMode = RequiredMode.REQUIRED,
                        example = "100")
                int units,
        @Schema(
                        type = "string",
                        format = "decimal",
                        requiredMode = RequiredMode.REQUIRED,
                        example = "13.755",
                        description = "Value of `units` of the currency in the base currency")
                String rate) {}
