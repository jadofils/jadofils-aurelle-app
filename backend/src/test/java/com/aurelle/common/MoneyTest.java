package com.aurelle.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MoneyTest {

    @Test
    void holdsMinorUnitsAndCurrency() {
        Money money = Money.of(15_000, "USD");

        assertThat(money.amountMinor()).isEqualTo(15_000L);
        assertThat(money.currency()).isEqualTo("USD");
        assertThat(money).isEqualTo(new Money(15_000, "USD"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"usd", "US", "USDT", "$", ""})
    void rejectsNonIsoCurrencies(String currency) {
        assertThatThrownBy(() -> Money.of(1, currency)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void requiresCurrency() {
        assertThatThrownBy(() -> Money.of(1, null)).isInstanceOf(NullPointerException.class);
    }
}
