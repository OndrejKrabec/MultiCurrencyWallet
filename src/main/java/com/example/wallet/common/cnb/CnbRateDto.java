package com.example.wallet.common.cnb;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Currency;

/**
 * One CNB quote, e.g. {@code {"validFor": "2026-11-03", "amount": 100, "currencyCode": "JPY",
 * "rate": 13.755}}: {@code amount} units of the currency are worth {@code rate} CZK.
 */
public record CnbRateDto(LocalDate validFor, int amount, Currency currencyCode, BigDecimal rate) {}
