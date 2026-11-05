package com.example.wallet.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Settings of the generated OpenAPI description ({@code wallet.api-docs.*}).
 *
 * @param serverUrl where clients reach the API, shown in Swagger UI and the spec
 * @param serverDescription a label for that server
 */
@Validated
@ConfigurationProperties("wallet.api-docs")
public record ApiDocsProperties(@NotNull URI serverUrl, @NotBlank String serverDescription) {}
