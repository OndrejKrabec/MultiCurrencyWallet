package com.example.wallet.domain.wallet;

import com.example.wallet.domain.wallet.dto.BalancesResponseDto;
import com.example.wallet.domain.wallet.dto.FundsRequestDto;
import com.example.wallet.domain.wallet.dto.WalletTotalResponseDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Currency;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/wallet", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(
        name = "Wallet",
        description =
                "Deposits, withdrawals and balances of the single wallet, and its total in any currency")
@ApiResponse(
        responseCode = "500",
        description = "Unexpected error; details are only logged",
        content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE))
public class WalletController {

    private final WalletService walletService;
    private final WalletMapper walletMapper;

    public WalletController(WalletService walletService, WalletMapper walletMapper) {
        this.walletService = walletService;
        this.walletMapper = walletMapper;
    }

    @Operation(
            summary = "Deposits money.",
            description =
                    "Adds the amount to the balance of its currency. Accepts the base currency of the rates (CZK) "
                            + "and every currency quoted against it. Returns all balances after the deposit.")
    @ApiResponse(responseCode = "200", description = "Deposited; all balances")
    @ApiResponse(
            responseCode = "400",
            description =
                    "Invalid input; `errors` lists each invalid field (absent for malformed JSON)",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE))
    @ApiResponse(
            responseCode = "422",
            description = "A currency CNB doesn't quote, or a pseudo-currency such as XDR",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE))
    @ApiResponse(
            responseCode = "503",
            description = "No valid exchange rates have been loaded yet",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE))
    @PostMapping(path = "/deposits", consumes = MediaType.APPLICATION_JSON_VALUE)
    public BalancesResponseDto deposit(@Valid @RequestBody FundsRequestDto request) {
        return walletMapper.toBalancesResponseDto(
                walletService.deposit(walletMapper.toFunds(request)));
    }

    @Operation(
            summary = "Withdraws money.",
            description =
                    "Subtracts the amount from the balance of its currency, atomically: either all of it or nothing. "
                            + "Never needs CNB, so any currency held can always be withdrawn. Returns all balances after the withdrawal.")
    @ApiResponse(responseCode = "200", description = "Withdrawn; all balances")
    @ApiResponse(
            responseCode = "400",
            description =
                    "Invalid input; `errors` lists each invalid field (absent for malformed JSON)",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE))
    @ApiResponse(
            responseCode = "409",
            description = "Amount exceeds the balance of that currency (0 if not held)",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE))
    @ApiResponse(
            responseCode = "422",
            description = "A pseudo-currency such as XDR, which a wallet can't hold",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE))
    @PostMapping(path = "/withdrawals", consumes = MediaType.APPLICATION_JSON_VALUE)
    public BalancesResponseDto withdraw(@Valid @RequestBody FundsRequestDto request) {
        return walletMapper.toBalancesResponseDto(
                walletService.withdraw(walletMapper.toFunds(request)));
    }

    @Operation(
            summary = "Returns the balances.",
            description =
                    "Every currency ever deposited, sorted by currency code; a fully withdrawn one shows zero.")
    @ApiResponse(responseCode = "200", description = "All balances")
    @GetMapping
    public BalancesResponseDto balances() {
        return walletMapper.toBalancesResponseDto(walletService.balances());
    }

    @Operation(
            summary = "Returns the total of all balances in one currency.",
            description =
                    "Converts every balance through the base currency (CZK) with the latest CNB fixing, sums "
                            + "the exact amounts and rounds once, down to the currency's minor units. The response says which fixing was used.")
    @ApiResponse(
            responseCode = "200",
            description = "The total and the CNB fixing it was calculated with")
    @ApiResponse(
            responseCode = "400",
            description = "Missing or invalid `currency`",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE))
    @ApiResponse(
            responseCode = "422",
            description =
                    "The target or a held currency is not quoted by CNB, or is a pseudo-currency such as XDR",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE))
    @ApiResponse(
            responseCode = "503",
            description = "No valid CNB rates have been loaded yet",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE))
    @GetMapping("/total")
    public WalletTotalResponseDto total(
            @Parameter(
                            description =
                                    "Target currency: CZK or any currency quoted by CNB (ISO 4217, upper case)",
                            required = true,
                            example = "EUR",
                            schema = @Schema(type = "string"))
                    @RequestParam
                    Currency currency) {
        return walletMapper.toWalletTotalResponseDto(walletService.calculateTotal(currency));
    }
}
