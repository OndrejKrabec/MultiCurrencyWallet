package com.example.wallet.domain.wallet.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import java.util.Currency;

@Schema(name = "Balance", description = "The balance of one currency")
public record BalanceDto(
        @Schema(
                        type = "string",
                        description = "ISO 4217 currency code",
                        requiredMode = RequiredMode.REQUIRED,
                        example = "USD")
                Currency currency,
        @Schema(
                        type = "string",
                        format = "decimal",
                        requiredMode = RequiredMode.REQUIRED,
                        example = "7.00",
                        description = "Amount with exactly the currency's minor units")
                String amount) {}
