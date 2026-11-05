package com.example.wallet.common.cnb;

import com.example.wallet.common.exception.RatesUnavailableException;
import com.example.wallet.domain.fxrate.FxRateProvider;
import com.example.wallet.domain.fxrate.FxRateTable;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Keeps the latest CNB fixing in memory; requests only read it. CNB publishes one fixing per
 * working day at about 14:30 Prague time, so the scheduled refresh calls CNB only when a newer one
 * can exist.
 */
@Component
class CnbFxRateProvider implements FxRateProvider {

    private static final ZoneId PRAGUE = ZoneId.of("Europe/Prague");
    private static final LocalTime PUBLICATION_TIME = LocalTime.of(14, 30);

    private static final Logger log = LoggerFactory.getLogger(CnbFxRateProvider.class);

    private final CnbClient client;
    private final CnbMapper mapper;
    private final Clock clock;

    // written only by the scheduler thread, read by requests
    private volatile @Nullable FxRateTable rates;
    private volatile boolean hasLastRefreshFailed;

    CnbFxRateProvider(CnbClient client, CnbMapper mapper, Clock clock) {
        this.client = client;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Override
    public FxRateTable getCurrentRates() {
        return findCurrentRates()
                .orElseThrow(() ->
                        new RatesUnavailableException(
                                "Exchange rates are not available yet; no valid CNB rates could be loaded"));
    }

    Optional<FxRateTable> findCurrentRates() {
        return Optional.ofNullable(rates);
    }

    boolean hasLastRefreshFailed() {
        return hasLastRefreshFailed;
    }

    @Scheduled(fixedDelayString = "${wallet.cnb.refresh-interval}")
    void refresh() {
        ZonedDateTime now = clock.instant().atZone(PRAGUE);
        if (!isNewerFixingPossible(now)) {
            return;
        }
        try {
            FxRateTable loaded = mapper.toFxRateTable(client.fetchDailyRates(now.toLocalDate()));
            rates = loaded;
            hasLastRefreshFailed = false;
            log.info("Loaded CNB rates valid for {}", loaded.validFor());
        } catch (RuntimeException e) {
            hasLastRefreshFailed = true;
            log.warn("CNB refresh failed, keeping the previous rates: {}", e.toString());
        }
    }

    /** At startup, after a failure, and on weekdays after 14:30 until today's fixing arrived. */
    private boolean isNewerFixingPossible(ZonedDateTime now) {
        FxRateTable current = rates;
        if (current == null || hasLastRefreshFailed) {
            return true;
        }
        boolean isWeekend =
                now.getDayOfWeek() == DayOfWeek.SATURDAY || now.getDayOfWeek() == DayOfWeek.SUNDAY;
        return !isWeekend
                && !now.toLocalTime().isBefore(PUBLICATION_TIME)
                && current.validFor().isBefore(now.toLocalDate());
    }
}
