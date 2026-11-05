package com.example.wallet.config;

import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.boot.http.client.HttpRedirects;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * The HTTP client for the CNB API: base URL and timeouts from {@link CnbProperties}, redirects not
 * followed.
 */
@Configuration(proxyBeanMethods = false)
public class CnbClientConfig {

    public static final String CNB_REST_CLIENT = "cnbRestClient";

    @Bean(CNB_REST_CLIENT)
    public RestClient cnbRestClient(RestClient.Builder builder, CnbProperties properties) {
        var settings =
                HttpClientSettings.defaults()
                        .withTimeouts(properties.connectTimeout(), properties.readTimeout())
                        .withRedirects(HttpRedirects.DONT_FOLLOW);
        return builder.baseUrl(properties.baseUrl().toString())
                .requestFactory(ClientHttpRequestFactoryBuilder.jdk().build(settings))
                .build();
    }
}
