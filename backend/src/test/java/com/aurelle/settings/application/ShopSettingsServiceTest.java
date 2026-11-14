package com.aurelle.settings.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aurelle.common.Money;
import com.aurelle.organization.OrganizationSummary;
import com.aurelle.settings.domain.ShopSettings;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ShopSettingsServiceTest {

    private static final OrganizationSummary ORG = new OrganizationSummary(
        UUID.randomUUID(), "Aurelle", "USD", "help@aurelle.example", "+250788000000");

    @Test
    void combinesShopSettingsWithOrganizationContacts() {
        var settings = new ShopSettings("Aurelle", "USD", "$", 15_000L, 30, "Made to be worn on repeat.", true);
        var service = new ShopSettingsService(new Directory(), id -> id.equals(ORG.id()) ? Optional.of(settings) : Optional.empty());

        PublicShopSettings result = service.publicSettings();

        assertThat(result).isEqualTo(new PublicShopSettings("Aurelle", "USD", "$", Money.of(15_000, "USD"), 30,
            "Made to be worn on repeat.", "help@aurelle.example", "+250788000000", true));
    }

    @Test
    void freeShippingIsAbsentWhenNoThresholdIsSet() {
        var settings = new ShopSettings("Aurelle", "USD", null, null, 14, null, false);
        var service = new ShopSettingsService(new Directory(), id -> Optional.of(settings));

        PublicShopSettings result = service.publicSettings();

        assertThat(result.freeShippingThreshold()).isNull();
        assertThat(result.guestCheckoutEnabled()).isFalse();
    }

    /** Test double: only the default organization is used by the service. */
    private static final class Directory implements com.aurelle.organization.OrganizationDirectory {
        @Override
        public OrganizationSummary defaultOrganization() {
            return ORG;
        }

        @Override
        public java.util.List<com.aurelle.organization.LocationSummary> locations(UUID organizationId) {
            return java.util.List.of();
        }

        @Override
        public Optional<com.aurelle.organization.LocationSummary> location(UUID locationId) {
            return Optional.empty();
        }
    }

    @Test
    void failsFastWhenSettingsAreMissing() {
        var service = new ShopSettingsService(new Directory(), id -> Optional.empty());

        assertThatThrownBy(service::publicSettings)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining(ORG.id().toString());
    }
}
