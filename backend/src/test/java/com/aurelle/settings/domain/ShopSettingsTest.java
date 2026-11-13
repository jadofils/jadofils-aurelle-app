package com.aurelle.settings.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aurelle.common.Money;
import org.junit.jupiter.api.Test;

class ShopSettingsTest {

    @Test
    void exposesTheFreeShippingThresholdInTheShopCurrency() {
        var settings = new ShopSettings("Aurelle", "RWF", "FRw", 50_000L, 30, null, true);

        assertThat(settings.freeShippingThreshold()).contains(Money.of(50_000, "RWF"));
    }

    @Test
    void noThresholdMeansNoFreeShipping() {
        assertThat(new ShopSettings("Aurelle", "USD", null, null, 30, null, true).freeShippingThreshold()).isEmpty();
    }

    @Test
    void zeroThresholdIsAllowed() {
        assertThat(new ShopSettings("Aurelle", "USD", null, 0L, 0, null, true).freeShippingThreshold())
            .contains(Money.of(0, "USD"));
    }

    @Test
    void rejectsInvalidValues() {
        assertThatThrownBy(() -> new ShopSettings("Aurelle", "USD", null, -1L, 30, null, true))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ShopSettings("Aurelle", "USD", null, null, -1, null, true))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ShopSettings(null, "USD", null, null, 30, null, true))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ShopSettings("Aurelle", null, null, null, 30, null, true))
            .isInstanceOf(NullPointerException.class);
    }
}
