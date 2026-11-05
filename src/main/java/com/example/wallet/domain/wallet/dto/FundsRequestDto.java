package com.example.wallet.domain.wallet.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.Currency;

/**
 * Body of a deposit or a withdrawal. Validates the shape only; currency-specific rules are the
 * domain's.
 */
@Schema(name = "FundsRequest", description = "An amount of money in one currency")
public record FundsRequestDto(
        @Schema(
                        type = "string",
                        description = "ISO 4217 currency code in upper case",
                        requiredMode = RequiredMode.REQUIRED,
                        example = "USD")
                @NotNull
                Currency currency,
        @Schema(
                        type = "string",
                        format = "decimal",
                        requiredMode = RequiredMode.REQUIRED,
                        example = "10.50",
                        description =
                                "Positive amount, preferably sent as a string (a JSON number is accepted too); "
                                        + "at most the currency's ISO 4217 decimal places (2 for USD, 0 for JPY)")
                @NotNull
                @Positive
                @Digits(
                        integer = 15,
                        fraction = 15,
                        message = "must have at most 15 integer and 15 decimal digits")
                BigDecimal amount) {}
