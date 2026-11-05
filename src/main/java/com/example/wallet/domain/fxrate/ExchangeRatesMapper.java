package com.example.wallet.domain.fxrate;

import com.example.wallet.domain.fxrate.dto.ExchangeRateDto;
import com.example.wallet.domain.fxrate.dto.ExchangeRatesResponseDto;
import java.util.Comparator;
import org.springframework.stereotype.Component;

@Component
public class ExchangeRatesMapper {

    public ExchangeRatesResponseDto toExchangeRatesResponseDto(FxRateTable table) {
        return new ExchangeRatesResponseDto(
                table.validFor(),
                table.base(),
                table.rates().values().stream()
                        .sorted(Comparator.comparing(rate -> rate.currency().getCurrencyCode()))
                        .map(rate ->
                                new ExchangeRateDto(
                                        rate.currency(),
                                        rate.units(),
                                        rate.rate().stripTrailingZeros().toPlainString()))
                        .toList());
    }
}
