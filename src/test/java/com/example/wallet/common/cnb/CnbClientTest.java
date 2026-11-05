package com.example.wallet.common.cnb;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.wallet.CnbFixtures;
import com.example.wallet.config.CnbClientConfig;
import com.example.wallet.config.CnbProperties;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Currency;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** The HTTP side of the CNB integration, against WireMock serving recorded CNB responses. */
class CnbClientTest {

    private static final LocalDate TUE = LocalDate.of(2026, 11, 3);
    private static final LocalDate SUN = LocalDate.of(2026, 11, 1);

    @RegisterExtension
    static WireMockExtension cnb =
            WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

    private CnbClient client;

    @BeforeEach
    void setUp() {
        client = clientFor(URI.create(cnb.baseUrl()));
    }

    @Test
    void fetchesTheFixingForTheRequestedDate() {
        stubDaily(TUE, okJson(CnbFixtures.read(CnbFixtures.TUESDAY)));

        CnbDailyRatesDto response = client.fetchDailyRates(TUE);

        assertThat(response.rates())
                .hasSize(30)
                .contains(
                        new CnbRateDto(
                                TUE, 100, Currency.getInstance("JPY"), new BigDecimal("13.755")));
        cnb.verify(
                getRequestedFor(urlPathEqualTo("/cnbapi/exrates/daily"))
                        .withQueryParam("date", equalTo("2026-11-03"))
                        .withQueryParam("lang", equalTo("EN")));
    }

    @Test
    void onAWeekendCnbAnswersWithTheLatestEarlierFixing() {
        stubDaily(SUN, okJson(CnbFixtures.read(CnbFixtures.SUNDAY)));

        assertThat(client.fetchDailyRates(SUN).rates())
                .allSatisfy(
                        rate -> assertThat(rate.validFor()).isEqualTo(LocalDate.of(2026, 10, 30)));
    }

    @Test
    void serverErrorIsUnavailable() {
        stubDaily(TUE, aResponse().withStatus(503));

        assertThatThrownBy(() -> client.fetchDailyRates(TUE))
                .isInstanceOf(RestClientException.class)
                .hasMessageStartingWith("503");
    }

    @Test
    void slowResponseTimesOut() {
        stubDaily(TUE, okJson(CnbFixtures.read(CnbFixtures.TUESDAY)).withFixedDelay(2_000));

        assertThatThrownBy(() -> client.fetchDailyRates(TUE))
                .isInstanceOf(RestClientException.class);
    }

    @Test
    void emptyBodyFails() {
        stubDaily(TUE, aResponse().withStatus(200).withHeader("Content-Type", "application/json"));

        assertThatThrownBy(() -> client.fetchDailyRates(TUE))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("CNB returned an empty body");
    }

    private static CnbClient clientFor(URI baseUrl) {
        var properties =
                new CnbProperties(
                        baseUrl,
                        Duration.ofSeconds(1),
                        Duration.ofSeconds(1),
                        Duration.ofMinutes(10));
        return new CnbClient(new CnbClientConfig().cnbRestClient(RestClient.builder(), properties));
    }

    private static void stubDaily(LocalDate date, ResponseDefinitionBuilder response) {
        cnb.stubFor(
                get(urlPathEqualTo("/cnbapi/exrates/daily"))
                        .withQueryParam("date", equalTo(date.toString()))
                        .willReturn(response));
    }
}
