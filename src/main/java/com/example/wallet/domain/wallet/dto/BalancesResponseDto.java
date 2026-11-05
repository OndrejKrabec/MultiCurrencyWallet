package com.example.wallet.domain.wallet.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import java.util.List;

@Schema(name = "BalancesResponse", description = "All balances of the wallet")
public record BalancesResponseDto(
        @Schema(
                        description =
                                "Every currency ever deposited, sorted by currency code; a fully withdrawn one shows "
                                        + "zero; empty for a new wallet",
                        requiredMode = RequiredMode.REQUIRED)
                List<BalanceDto> balances) {

    public BalancesResponseDto {
        balances = List.copyOf(balances);
    }
}
