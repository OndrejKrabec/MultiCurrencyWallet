package com.example.wallet;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.wallet.common.cnb.CnbRefresh;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import com.jayway.jsonpath.JsonPath;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.web.client.RestClient;

/**
 * The whole application over real HTTP, with CNB replaced by WireMock serving recorded responses
 * and the clock pinned. Each test gets a fresh context, i.e. an empty wallet and an empty rate
 * cache. The test profile doesn't schedule the CNB refresh; {@link #refreshRates()} runs it.
 */
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class WalletEndToEndTest {

    /** Tuesday 2026-11-03, 16:00 in Prague: after that day's CNB fixing was published. */
    private static final Instant TUESDAY_AFTERNOON = Instant.parse("2026-11-03T15:00:00Z");

    /**
     * Static because @TestBean needs a static factory; safe as long as tests in this class run
     * sequentially.
     */
    private static final MutableClock CLOCK = new MutableClock(TUESDAY_AFTERNOON);

    @RegisterExtension
    static WireMockExtension cnb =
            WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

    @DynamicPropertySource
    static void cnbBaseUrl(DynamicPropertyRegistry registry) {
        registry.add("wallet.cnb.base-url", cnb::baseUrl);
    }

    @TestBean
    @SuppressWarnings("unused") // read by Spring: @TestBean replaces the Clock bean with clock()
    private Clock clock;

    static Clock clock() {
        return CLOCK;
    }

    @Autowired private ApplicationContext context;

    @LocalServerPort private int port;

    private RestClient http;

    @BeforeEach
    void setUp() {
        CLOCK.set(TUESDAY_AFTERNOON);
        http = RestClient.builder().baseUrl("http://localhost:" + port).build();
    }

    @Test
    void assignmentWorkflowWithRealCnbFixing() {
        stubCnb("2026-11-03", "daily-2026-11-03.json");
        refreshRates();

        httpPost("/wallet/deposits", "USD", "10");
        httpPost("/wallet/deposits", "EUR", "5");
        Response afterWithdrawal = httpPost("/wallet/withdrawals", "USD", "3");

        assertThat(afterWithdrawal.status()).isEqualTo(200);
        assertThat(afterWithdrawal.value("$.balances[?(@.currency == 'USD')].amount"))
                .asInstanceOf(InstanceOfAssertFactories.LIST)
                .containsExactly("7.00");

        // CNB 2026-11-03: 1 USD = 21.762 CZK, 1 EUR = 24.34 CZK
        // 7 + 5 × 24.34 / 21.762 = 12.5923… USD and 5 + 7 × 21.762 / 24.34 = 11.2585… EUR,
        // rounded down: 12.59 USD and 11.25 EUR
        Response inUsd = httpGet("/wallet/total?currency=USD");
        assertThat(inUsd.string("$.amount")).isEqualTo("12.59");
        assertThat(inUsd.string("$.ratesValidFor")).isEqualTo("2026-11-03");
        assertThat(httpGet("/wallet/total?currency=EUR").string("$.amount")).isEqualTo("11.25");
    }

    @Test
    void failedCnbCallIsRetriedWithinTheSameRefresh() {
        cnb.stubFor(
                get(urlPathEqualTo("/cnbapi/exrates/daily"))
                        .inScenario("blip")
                        .whenScenarioStateIs(Scenario.STARTED)
                        .willReturn(aResponse().withStatus(503))
                        .willSetStateTo("back"));
        cnb.stubFor(
                get(urlPathEqualTo("/cnbapi/exrates/daily"))
                        .inScenario("blip")
                        .whenScenarioStateIs("back")
                        .willReturn(okJson(fixture("daily-2026-11-03.json"))));

        refreshRates();

        cnb.verify(2, getRequestedFor(urlPathEqualTo("/cnbapi/exrates/daily")));
        assertThat(httpGet("/wallet/total?currency=USD").status()).isEqualTo(200);
        assertThat(httpGet("/actuator/health").string("$.components.cnb.status")).isEqualTo("UP");
    }

    @Test
    void cnbOutageAfterRatesWereLoadedKeepsTheLastFixing() {
        // afternoon, but CNB still returns an older fixing (late), so the next run asks again
        stubCnb("2026-11-03", "daily-2026-10-30.json");
        refreshRates();
        httpPost("/wallet/deposits", "USD", "1");

        cnb.resetAll();
        cnb.stubFor(
                get(urlPathEqualTo("/cnbapi/exrates/daily"))
                        .willReturn(aResponse().withStatus(503)));
        CLOCK.advance(Duration.ofMinutes(10));
        refreshRates();

        Response total = httpGet("/wallet/total?currency=CZK");
        assertThat(total.status()).isEqualTo(200);
        assertThat(total.string("$.ratesValidFor")).isEqualTo("2026-10-30");
        Response health = httpGet("/actuator/health");
        assertThat(health.string("$.components.cnb.status")).isEqualTo("UNKNOWN");
        assertThat(health.bool("$.components.cnb.details.hasLastRefreshFailed")).isTrue();
    }

    @Test
    void cnbNeverReachableMeansNoDepositsOrTotalsButBalancesStillWork() {
        cnb.stubFor(
                get(urlPathEqualTo("/cnbapi/exrates/daily"))
                        .willReturn(aResponse().withStatus(500)));
        refreshRates();

        assertThat(httpGet("/wallet").status()).isEqualTo(200);
        for (Response response :
                new Response[] {
                    httpPost("/wallet/deposits", "CZK", "100"),
                    httpGet("/wallet/total?currency=CZK")
                }) {
            assertThat(response.status()).isEqualTo(503);
            assertThat(response.contentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        }
    }

    private void refreshRates() {
        CnbRefresh.now(context);
    }

    private Response httpPost(String uri, String currency, String amount) {
        return http.post()
                .uri(uri)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"currency\": \"%s\", \"amount\": \"%s\"}".formatted(currency, amount))
                .exchange((request, response) ->
                        Response.of(
                                response.getStatusCode().value(),
                                response.getHeaders(),
                                new String(
                                        response.getBody().readAllBytes(),
                                        StandardCharsets.UTF_8)));
    }

    private Response httpGet(String uri) {
        return http.get()
                .uri(uri)
                .exchange((request, response) ->
                        Response.of(
                                response.getStatusCode().value(),
                                response.getHeaders(),
                                new String(
                                        response.getBody().readAllBytes(),
                                        StandardCharsets.UTF_8)));
    }

    private static void stubCnb(String date, String fixture) {
        cnb.stubFor(
                get(urlPathEqualTo("/cnbapi/exrates/daily"))
                        .withQueryParam("date", equalTo(date))
                        .willReturn(okJson(fixture(fixture))));
    }

    private static String fixture(String name) {
        try (var in = WalletEndToEndTest.class.getResourceAsStream("/cnb/" + name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private record Response(int status, String contentType, String body) {

        static Response of(int status, HttpHeaders headers, String body) {
            return new Response(status, headers.getFirst("Content-Type"), body);
        }

        String string(String path) {
            return JsonPath.read(body, path);
        }

        boolean bool(String path) {
            return JsonPath.read(body, path);
        }

        Object value(String path) {
            return JsonPath.read(body, path);
        }
    }
}
