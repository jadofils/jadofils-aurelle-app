package com.aurelle.settings.application;

import com.aurelle.settings.domain.ShopSettings;
import java.util.Optional;
import java.util.UUID;

/** Persistence port for shop settings. */
public interface ShopSettingsRepository {

    Optional<ShopSettings> findByOrganizationId(UUID organizationId);
}
