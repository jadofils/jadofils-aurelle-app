package com.aurelle.common;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Exact money: integer minor units plus an ISO 4217 currency code. Never floating point.
 *
 * @param amountMinor amount in the currency's minor unit (e.g. cents)
 * @param currency    ISO 4217 alphabetic code, e.g. {@code USD}
 */
public record Money(long amountMinor, String currency) {

    private static final Pattern ISO_4217 = Pattern.compile("[A-Z]{3}");

    public Money {
        Objects.requireNonNull(currency, "currency");
        if (!ISO_4217.matcher(currency).matches()) {
            throw new IllegalArgumentException("currency must be a 3-letter ISO 4217 code");
        }
    }

    public static Money of(long amountMinor, String currency) {
        return new Money(amountMinor, currency);
    }
}
