package com.aurelle.organization;

import java.util.Objects;
import java.util.UUID;

/**
 * Read-only view of an organization for other modules.
 *
 * @param id              organization ID
 * @param name            brand/legal name
 * @param defaultCurrency ISO 4217 code
 * @param supportEmail    public support e-mail, if configured
 * @param supportPhone    public support phone (E.164), if configured
 */
public record OrganizationSummary(UUID id, String name, String defaultCurrency, String supportEmail,
        String supportPhone) {

    public OrganizationSummary {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(defaultCurrency, "defaultCurrency");
    }
}
