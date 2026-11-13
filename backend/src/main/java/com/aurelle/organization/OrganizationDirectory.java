package com.aurelle.organization;

/**
 * Public API of the organization module. Other modules use this instead of reading the
 * {@code organization} table.
 *
 * <p>Until multi-tenancy is decided (docs/decisions.md D15) there is one default organization,
 * selected by {@code aurelle.organization.default-slug}.
 */
public interface OrganizationDirectory {

    /** The default organization. Fails fast if it is missing (a broken deployment, not a user error). */
    OrganizationSummary defaultOrganization();
}
