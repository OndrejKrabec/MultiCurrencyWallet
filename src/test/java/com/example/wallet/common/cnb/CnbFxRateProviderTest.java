package com.example.wallet.common.cnb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.wallet.MutableClock;
import com.example.wallet.common.exception.RatesUnavailableException;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Currency;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;

/** Prague is UTC+1 in November: 14:30 in Prague is 13:30Z. */
class CnbFxRateProviderTest {

    private static final LocalDate FRI = LocalDate.of(2026, 10, 30);
    private static final LocalDate MON = LocalDate.of(2026, 11, 2);
    private static final LocalDate TUE = LocalDate.of(2026, 11, 3);

    private static final Instant TUE_MORNING = Instant.parse("2026-11-03T08:00:00Z");
    private static final Instant TUE_AFTERNOON = Instant.parse("2026-11-03T15:00:00Z");

    private final CnbClient client = mock(CnbClient.class);
    private final MutableClock clock = new MutableClock(TUE_AFTERNOON);
    private final CnbFxRateProvider provider =
            new CnbFxRateProvider(client, new CnbMapper(), clock);

    @Test
    void firstRefreshFetchesAtOnceEvenAtNight() {
        clock.set(Instant.parse("2026-11-03T01:00:00Z"));
        when(client.fetchDailyRates(TUE)).thenReturn(cnbRates(MON));

        provider.refresh();

        assertThat(provider.getCurrentRates().validFor()).isEqualTo(MON);
    }

    @Test
    void requestsTodayInPragueNotUtc() {
        // 23:30 UTC on Nov 3 is already 00:30 on Nov 4 in Prague
        clock.set(Instant.parse("2026-11-03T23:30:00Z"));
        when(client.fetchDailyRates(any())).thenReturn(cnbRates(TUE));

        provider.refresh();

        verify(client).fetchDailyRates(LocalDate.of(2026, 11, 4));
    }

    @Test
    void doesNotCallCnbBeforeThePublicationTime() {
        clock.set(TUE_MORNING);
        when(client.fetchDailyRates(TUE)).thenReturn(cnbRates(MON), cnbRates(TUE));
        provider.refresh();

        clock.set(Instant.parse("2026-11-03T13:29:00Z"));
        provider.refresh();
        verify(client, times(1)).fetchDailyRates(any());

        clock.set(Instant.parse("2026-11-03T13:30:00Z"));
        provider.refresh();
        verify(client, times(2)).fetchDailyRates(any());
        assertThat(provider.getCurrentRates().validFor()).isEqualTo(TUE);
    }

    @Test
    void keepsCallingAfterThePublicationTimeUntilTodaysFixingArrivesThenStops() {
        when(client.fetchDailyRates(TUE)).thenReturn(cnbRates(MON), cnbRates(MON), cnbRates(TUE));

        provider.refresh();
        provider.refresh();
        provider.refresh();
        assertThat(provider.getCurrentRates().validFor()).isEqualTo(TUE);

        clock.advance(Duration.ofHours(5));
        provider.refresh();
        verify(client, times(3)).fetchDailyRates(any());
    }

    @Test
    void doesNotCallCnbOnWeekends() {
        clock.set(Instant.parse("2026-10-31T15:00:00Z")); // Saturday
        when(client.fetchDailyRates(any())).thenReturn(cnbRates(FRI));
        provider.refresh();

        provider.refresh();
        clock.advance(Duration.ofDays(1)); // Sunday
        provider.refresh();

        verify(client, times(1)).fetchDailyRates(any());
        assertThat(provider.getCurrentRates().validFor()).isEqualTo(FRI);
    }

    @Test
    void failedRefreshKeepsTheRatesAndIsRetriedOnTheNextRunEvenOnAWeekend() {
        clock.set(Instant.parse("2026-10-31T15:00:00Z")); // Saturday
        when(client.fetchDailyRates(any()))
                .thenReturn(cnbRates(FRI))
                .thenThrow(new ResourceAccessException("down"))
                .thenReturn(cnbRates(LocalDate.of(2026, 11, 6)));
        provider.refresh();
        clock.set(Instant.parse("2026-11-02T15:00:00Z")); // Monday afternoon

        provider.refresh();
        assertThat(provider.getCurrentRates().validFor()).isEqualTo(FRI);
        assertThat(provider.hasLastRefreshFailed()).isTrue();

        clock.set(
                Instant.parse("2026-11-07T10:00:00Z")); // next Saturday: we may have missed fixings
        provider.refresh();

        assertThat(provider.getCurrentRates().validFor()).isEqualTo(LocalDate.of(2026, 11, 6));
        assertThat(provider.hasLastRefreshFailed()).isFalse();
    }

    @Test
    void withoutRatesRequestsAreRejected() {
        when(client.fetchDailyRates(any())).thenThrow(new ResourceAccessException("down"));

        provider.refresh();

        assertThatThrownBy(provider::getCurrentRates).isInstanceOf(RatesUnavailableException.class);
    }

    @Test
    void requestsNeverCallCnb() {
        assertThatThrownBy(provider::getCurrentRates).isInstanceOf(RatesUnavailableException.class);

        verify(client, never()).fetchDailyRates(any());
    }

    /** A CNB response with one USD quote valid for {@code validFor}. */
    private static CnbDailyRatesDto cnbRates(LocalDate validFor) {
        return new CnbDailyRatesDto(
                List.of(
                        new CnbRateDto(
                                validFor,
                                1,
                                Currency.getInstance("USD"),
                                new BigDecimal("21.042"))));
    }
}
