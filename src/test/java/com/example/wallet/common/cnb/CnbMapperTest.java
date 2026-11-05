package com.example.wallet.common.cnb;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.wallet.CnbFixtures;
import com.example.wallet.domain.fxrate.FxRate;
import com.example.wallet.domain.fxrate.FxRateTable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Currency;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class CnbMapperTest {

    private static final LocalDate TUE = LocalDate.of(2026, 11, 3);
    private static final Currency USD = Currency.getInstance("USD");
    private static final Currency JPY = Currency.getInstance("JPY");

    private final CnbMapper mapper = new CnbMapper();

    @Test
    void mapsQuotesKeepingTheirUnitCountsAgainstCzk() {
        FxRateTable table =
                mapper.toFxRateTable(
                        new CnbDailyRatesDto(
                                List.of(
                                        new CnbRateDto(TUE, 1, USD, new BigDecimal("21.762")),
                                        new CnbRateDto(TUE, 100, JPY, new BigDecimal("13.755")))));

        assertThat(table.base()).isEqualTo(Currency.getInstance("CZK"));
        assertThat(table.validFor()).isEqualTo(TUE);
        assertThat(table.findRate(JPY)).contains(new FxRate(JPY, 100, new BigDecimal("13.755")));
        assertThat(table.findRate(USD)).contains(new FxRate(USD, 1, new BigDecimal("21.762")));
    }

    @Test
    void mapsARealResponse() {
        CnbDailyRatesDto response =
                JsonMapper.builder()
                        .build()
                        .readValue(CnbFixtures.read(CnbFixtures.TUESDAY), CnbDailyRatesDto.class);

        assertThat(mapper.toFxRateTable(response).rates()).hasSize(30);
    }
}
