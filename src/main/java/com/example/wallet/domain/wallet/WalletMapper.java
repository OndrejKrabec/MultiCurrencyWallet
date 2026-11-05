package com.example.wallet.domain.wallet;

import com.example.wallet.common.exception.InvalidAmountException;
import com.example.wallet.common.exception.UnsupportedCurrencyException;
import com.example.wallet.common.money.Funds;
import com.example.wallet.domain.wallet.dto.BalanceDto;
import com.example.wallet.domain.wallet.dto.BalancesResponseDto;
import com.example.wallet.domain.wallet.dto.FundsRequestDto;
import com.example.wallet.domain.wallet.dto.WalletTotalResponseDto;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.Currency;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Maps between the wallet's API DTOs and the domain. Display order (by currency code) is decided
 * here.
 */
@Component
public class WalletMapper {

    private static final Comparator<Currency> BY_CODE =
            Comparator.comparing(Currency::getCurrencyCode);

    /**
     * The amount a client asked to move; the request is already validated.
     *
     * @throws UnsupportedCurrencyException if the currency is not one a wallet can hold (a
     *     pseudo-currency)
     * @throws InvalidAmountException if the amount has more decimal places than the currency allows
     */
    public Funds toFunds(FundsRequestDto request) {
        return new Funds(request.currency(), request.amount());
    }

    public BalancesResponseDto toBalancesResponseDto(Map<Currency, BigDecimal> balances) {
        return new BalancesResponseDto(
                balances.entrySet().stream()
                        .sorted(Map.Entry.comparingByKey(BY_CODE))
                        .map(balance ->
                                new BalanceDto(
                                        balance.getKey(),
                                        new Funds(balance.getKey(), balance.getValue())
                                                .toPlainString()))
                        .toList());
    }

    public WalletTotalResponseDto toWalletTotalResponseDto(WalletTotal total) {
        return new WalletTotalResponseDto(
                total.total().currency(), total.total().toPlainString(), total.ratesValidFor());
    }
}
