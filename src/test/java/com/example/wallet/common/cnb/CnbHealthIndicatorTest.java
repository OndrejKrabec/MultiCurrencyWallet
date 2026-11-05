package com.example.wallet.common.cnb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.wallet.domain.fxrate.FxRateTable;
import java.time.LocalDate;
import java.util.Currency;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Status;

class CnbHealthIndicatorTest {

    private static final Currency CZK = Currency.getInstance("CZK");

    private final CnbFxRateProvider provider = mock(CnbFxRateProvider.class);
    private final CnbHealthIndicator indicator = new CnbHealthIndicator(provider);
    private final FxRateTable table = new FxRateTable(CZK, LocalDate.of(2026, 11, 3), Map.of());

    @Test
    void unknownBeforeAnyRatesWereLoaded() {
        when(provider.findCurrentRates()).thenReturn(Optional.empty());

        var health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UNKNOWN);
        assertThat(health.getDetails()).containsEntry("rates", "not loaded yet");
    }

    @Test
    void upWithFreshRates() {
        when(provider.findCurrentRates()).thenReturn(Optional.of(table));

        var health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails())
                .containsEntry("ratesValidFor", "2026-11-03")
                .containsEntry("hasLastRefreshFailed", false);
    }

    @Test
    void failedRefreshIsUnknownNeverDown() {
        when(provider.findCurrentRates()).thenReturn(Optional.of(table));
        when(provider.hasLastRefreshFailed()).thenReturn(true);

        assertThat(indicator.health().getStatus()).isEqualTo(Status.UNKNOWN);
    }
}
