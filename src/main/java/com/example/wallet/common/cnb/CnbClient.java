package com.example.wallet.common.cnb;

import com.example.wallet.config.CnbClientConfig;
import java.time.LocalDate;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * HTTP client of the CNB API. For a day without a fixing (weekend, holiday, today before ~14:30)
 * CNB answers with the latest earlier fixing; its {@code validFor} tells which one.
 */
@Component
public class CnbClient {

    private static final Logger log = LoggerFactory.getLogger(CnbClient.class);
    private static final String DAILY_RATES_PATH = "/cnbapi/exrates/daily?date={date}&lang=EN";

    private final RestClient restClient;

    public CnbClient(@Qualifier(CnbClientConfig.CNB_REST_CLIENT) RestClient restClient) {
        this.restClient = restClient;
    }

    /**
     * The fixing valid for {@code date}. A failed call is retried a few times with a growing pause
     * (by default 2, 4, 8 and 16 s), so a short outage, e.g. at startup, doesn't leave the app
     * without rates until the next scheduled refresh.
     *
     * @throws RestClientException if CNB cannot be reached or answers with an error, also after the
     *     retries
     */
    @Retryable(
            includes = RestClientException.class,
            maxRetries = 4,
            delayString = "${wallet.cnb.retry-delay}",
            multiplier = 2)
    public CnbDailyRatesDto fetchDailyRates(LocalDate date) {
        log.debug("Requesting the CNB fixing for {}", date);
        return Objects.requireNonNull(
                restClient
                        .get()
                        .uri(DAILY_RATES_PATH, date)
                        .retrieve()
                        .body(CnbDailyRatesDto.class),
                "CNB returned an empty body");
    }
}
