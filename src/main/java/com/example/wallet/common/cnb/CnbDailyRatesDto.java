package com.example.wallet.common.cnb;

import java.util.List;

/** Body of CNB's daily rates response: all quotes of one fixing. */
public record CnbDailyRatesDto(List<CnbRateDto> rates) {

    public CnbDailyRatesDto {
        rates = List.copyOf(rates);
    }
}
