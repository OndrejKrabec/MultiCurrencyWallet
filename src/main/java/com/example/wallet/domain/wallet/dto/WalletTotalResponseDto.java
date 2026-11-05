package com.example.wallet.domain.wallet.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import java.time.LocalDate;
import java.util.Currency;

@Schema(name = "WalletTotalResponse", description = "The total of all balances in one currency")
public record WalletTotalResponseDto(
        @Schema(
                        type = "string",
                        description = "Target currency",
                        requiredMode = RequiredMode.REQUIRED,
                        example = "EUR")
                Currency currency,
        @Schema(
                        type = "string",
                        format = "decimal",
                        requiredMode = RequiredMode.REQUIRED,
                        example = "11.25",
                        description =
                                "All balances converted to the currency and summed, then rounded once, down, to its minor units")
                String amount,
        @Schema(
                        requiredMode = RequiredMode.REQUIRED,
                        example = "2026-11-03",
                        description =
                                "Date of the CNB fixing used; earlier than today on weekends, holidays and before "
                                        + "~14:30 Prague time")
                LocalDate ratesValidFor) {}
