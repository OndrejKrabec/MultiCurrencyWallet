package com.example.wallet.common.cnb;

import com.example.wallet.domain.fxrate.FxRate;
import com.example.wallet.domain.fxrate.FxRateTable;
import java.util.Currency;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Maps CNB's response to an {@link FxRateTable}. CNB is a trusted source; its data is used as is.
 */
@Component
public class CnbMapper {

    /** CNB quotes every rate in Czech crowns. */
    static final Currency BASE = Currency.getInstance("CZK");

    public FxRateTable toFxRateTable(CnbDailyRatesDto response) {
        var rates =
                response.rates().stream()
                        .map(rate -> new FxRate(rate.currencyCode(), rate.amount(), rate.rate()))
                        .collect(Collectors.toMap(FxRate::currency, rate -> rate));
        return new FxRateTable(BASE, response.rates().getFirst().validFor(), rates);
    }
}
