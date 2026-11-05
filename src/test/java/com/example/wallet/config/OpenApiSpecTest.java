package com.example.wallet.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Keeps the committed {@code docs/openapi.yaml} in sync with the code. After an intended API
 * change, regenerate it with {@code ./mvnw test -Dtest=OpenApiSpecTest -Dopenapi.update=true}.
 */
@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class OpenApiSpecTest {

    private static final Path SPEC = Path.of("docs/openapi.yaml");

    @Autowired private MockMvc mvc;

    @Test
    void committedSpecMatchesGeneratedSpec() throws Exception {
        String generated =
                mvc.perform(get("/v3/api-docs.yaml"))
                        .andReturn()
                        .getResponse()
                        .getContentAsString(StandardCharsets.UTF_8);

        if (Boolean.getBoolean("openapi.update")) {
            Files.createDirectories(SPEC.getParent());
            Files.writeString(SPEC, generated);
        }

        assertThat(SPEC)
                .as("docs/openapi.yaml is missing; run with -Dopenapi.update=true")
                .exists();
        assertThat(Files.readString(SPEC))
                .as("docs/openapi.yaml is out of date; run with -Dopenapi.update=true")
                .isEqualTo(generated);
    }
}
