package com.aurelle.settings.application;

import com.aurelle.organization.OrganizationDirectory;
import com.aurelle.organization.OrganizationSummary;
import com.aurelle.settings.domain.ShopSettings;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Use cases for shop settings. */
@Service
public class ShopSettingsService {

    private final OrganizationDirectory organizations;
    private final ShopSettingsRepository repository;

    public ShopSettingsService(OrganizationDirectory organizations, ShopSettingsRepository repository) {
        this.organizations = organizations;
        this.repository = repository;
    }

    /** Public settings of the default shop. Fails fast if the settings row is missing. */
    @Transactional(readOnly = true)
    public PublicShopSettings publicSettings() {
        OrganizationSummary organization = organizations.defaultOrganization();
        ShopSettings settings = repository.findByOrganizationId(organization.id())
            .orElseThrow(() -> new IllegalStateException(
                "Shop settings are missing for organization " + organization.id()));
        return new PublicShopSettings(
            settings.shopName(),
            settings.currency(),
            settings.currencyDisplaySymbol(),
            settings.freeShippingThreshold().orElse(null),
            settings.returnWindowDays(),
            settings.tagline(),
            organization.supportEmail(),
            organization.supportPhone(),
            settings.guestCheckoutEnabled());
    }
}
