package com.aurelle.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class EmailAddressesTest {

    @ParameterizedTest
    @ValueSource(strings = {"amina@example.test", "a.b+c@shop.example.test", "x@y.co"})
    void acceptsOrdinaryAddresses(String value) {
        assertThat(EmailAddresses.isValid(value)).isTrue();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"no-at-sign", "@example.test", "a@", "a@b", "two@@example.test", "a@b@c.test",
        "spa ce@example.test", "a@example.", "a@.test", "tab\t@example.test"})
    void rejectsMalformedAddresses(String value) {
        assertThat(EmailAddresses.isValid(value)).isFalse();
    }

    @Test
    void rejectsOverlongAddresses() {
        assertThat(EmailAddresses.isValid("a".repeat(250) + "@example.test")).isFalse();
    }

    @Test
    void handlesHostileInputInLinearTime() {
        String hostile = "a".repeat(50_000) + "@" + "b.".repeat(25_000) + "!";
        String noDot = "x@" + "y".repeat(100_000);

        assertTimeoutPreemptively(Duration.ofMillis(200), () -> {
            assertThat(EmailAddresses.isValid(hostile)).isFalse();
            assertThat(EmailAddresses.isValid(noDot)).isFalse();
        });
    }
}
