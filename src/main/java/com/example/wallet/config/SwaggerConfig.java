package com.example.wallet.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI description of the API, generated from the code by springdoc: served at {@code
 * /v3/api-docs}, browsable at {@code /swagger-ui.html}, and committed as {@code docs/openapi.yaml}.
 */
@Configuration(proxyBeanMethods = false)
public class SwaggerConfig {

    /** Version of the API contract, not of the build: it changes only when the contract changes. */
    private static final String API_VERSION = "1.0";

    @Bean
    OpenAPI walletOpenApi(ApiDocsProperties properties) {
        return new OpenAPI()
                .info(
                        new Info()
                                .title("Multicurrency Wallet API")
                                .version(API_VERSION)
                                .description(
                                        """
                                        A single in-memory wallet holding money in many currencies, valued with Czech National \
                                        Bank (CNB) exchange rates. Amounts are decimal strings with the currency's minor units. \
                                        Errors are RFC 9457 Problem Details (application/problem+json).\
                                        """))
                .addServersItem(
                        new Server()
                                .url(properties.serverUrl().toString())
                                .description(properties.serverDescription()));
    }
}
