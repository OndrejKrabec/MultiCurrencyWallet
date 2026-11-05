package com.example.wallet;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/** Real CNB responses recorded in {@code src/test/resources/cnb} (see the README there). */
public final class CnbFixtures {

    /** {@code date=2026-11-03}, a Tuesday: 30 currencies valid for 2026-11-03. */
    public static final String TUESDAY = "daily-2026-11-03.json";

    /**
     * {@code date=2026-11-01}, a Sunday: CNB answers with Friday's fixing, valid for 2026-10-30.
     */
    public static final String SUNDAY = "daily-2026-10-30.json";

    private CnbFixtures() {}

    public static String read(String name) {
        try (var in = CnbFixtures.class.getResourceAsStream("/cnb/" + name)) {
            if (in == null) {
                throw new IllegalArgumentException("No fixture " + name);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
