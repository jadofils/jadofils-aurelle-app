package com.aurelle.organization;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class OrganizationSummaryTest {

    @Test
    void requiresIdNameAndCurrency() {
        UUID id = UUID.randomUUID();
        assertThatThrownBy(() -> new OrganizationSummary(null, "Aurelle", "USD", null, null))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new OrganizationSummary(id, null, "USD", null, null))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new OrganizationSummary(id, "Aurelle", null, null, null))
            .isInstanceOf(NullPointerException.class);
    }
}
