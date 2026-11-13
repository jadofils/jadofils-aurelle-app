package com.aurelle.organization.internal;

import com.aurelle.organization.OrganizationDirectory;
import com.aurelle.organization.OrganizationSummary;
import jakarta.validation.constraints.NotBlank;
import java.util.UUID;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/** Reads organizations with plain SQL (decision D4: JdbcClient adapters). */
@Component
@EnableConfigurationProperties(JdbcOrganizationDirectory.Properties.class)
class JdbcOrganizationDirectory implements OrganizationDirectory {

    private final JdbcClient jdbc;
    private final String defaultSlug;

    JdbcOrganizationDirectory(JdbcClient jdbc, Properties properties) {
        this.jdbc = jdbc;
        this.defaultSlug = properties.defaultSlug();
    }

    @Override
    public OrganizationSummary defaultOrganization() {
        return jdbc.sql("""
                select id, name, default_currency, support_email, support_phone
                  from organization
                 where slug = :slug and archived_at is null
                """)
            .param("slug", defaultSlug)
            .query((rs, row) -> new OrganizationSummary(
                rs.getObject("id", UUID.class),
                rs.getString("name"),
                rs.getString("default_currency"),
                rs.getString("support_email"),
                rs.getString("support_phone")))
            .optional()
            .orElseThrow(() -> new IllegalStateException(
                "Default organization '" + defaultSlug + "' is missing; check migrations and aurelle.organization.default-slug"));
    }

    @Override
    public java.util.List<com.aurelle.organization.LocationSummary> locations(UUID organizationId) {
        return jdbc.sql("""
                select id, name, kind, active from location
                 where organization_id = :org and archived_at is null
                 order by active desc, name, id
                """)
            .param("org", organizationId)
            .query((rs, row) -> new com.aurelle.organization.LocationSummary(rs.getObject("id", UUID.class),
                rs.getString("name"), rs.getString("kind"), rs.getBoolean("active")))
            .list();
    }

    @Override
    public java.util.Optional<com.aurelle.organization.LocationSummary> location(UUID locationId) {
        return jdbc.sql("select id, name, kind, active from location where id = :id and archived_at is null")
            .param("id", locationId)
            .query((rs, row) -> new com.aurelle.organization.LocationSummary(rs.getObject("id", UUID.class),
                rs.getString("name"), rs.getString("kind"), rs.getBoolean("active")))
            .optional();
    }

    /** {@code aurelle.organization.*}. */
    @Validated
    @ConfigurationProperties("aurelle.organization")
    record Properties(@NotBlank String defaultSlug) {
    }
}
