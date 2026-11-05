package com.example.wallet.config;

import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Settings of the CNB API client and the rate cache ({@code wallet.cnb.*}).
 *
 * @param baseUrl CNB API root, e.g. {@code https://api.cnb.cz}
 * @param connectTimeout how long to wait for a TCP connection
 * @param readTimeout how long to wait for the response
 * @param retryDelay the pause before the first retry of a failed call; it doubles with each retry
 * @param refreshInterval how often the background refresh runs; CNB is called only when a newer
 *     fixing can exist
 */
@Validated
@ConfigurationProperties("wallet.cnb")
public record CnbProperties(
        @NotNull URI baseUrl,
        @NotNull Duration connectTimeout,
        @NotNull Duration readTimeout,
        @NotNull Duration retryDelay,
        @NotNull Duration refreshInterval) {}
