package com.aurelle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

/** Migrations apply on an empty database, seed the defaults, and enforce invariants in SQL. */
@Transactional // every test rolls back
class MigrationIT extends IntegrationTest {

    @Autowired
    JdbcClient jdbc;

    @Test
    void migrationsCreateTheDefaultOrganizationWithSettings() {
        assertThat(jdbc.sql("select count(*) from organization where slug = 'aurelle' and archived_at is null")
            .query(Long.class).single()).isEqualTo(1);
        assertThat(jdbc.sql("""
                select s.free_shipping_threshold_minor from shop_setting s
                  join organization o on o.id = s.organization_id where o.slug = 'aurelle'
                """).query(Long.class).single()).isEqualTo(15000L);
    }

    @Test
    void primaryKeysAreUuidV7() {
        UUID id = jdbc.sql("select id from organization where slug = 'aurelle'").query(UUID.class).single();
        assertThat(id.version()).isEqualTo(7);
    }

    @Test
    void slugsAreUniqueIgnoringCase() {
        assertThatThrownBy(() -> insertOrganization("AURELLE", "USD"))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void currencyMustBeIso4217() {
        assertThatThrownBy(() -> insertOrganization("other-shop", "usd"))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void oneSettingsRowPerOrganization() {
        assertThatThrownBy(() -> jdbc.sql("""
                insert into shop_setting (organization_id, shop_name, currency)
                select id, 'Second', 'USD' from organization where slug = 'aurelle'
                """).update())
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void moneyCannotBeNegative() {
        assertThatThrownBy(() -> jdbc.sql("update shop_setting set free_shipping_threshold_minor = -1").update())
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void returnWindowCannotBeNegative() {
        assertThatThrownBy(() -> jdbc.sql("update shop_setting set return_window_days = -1").update())
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    private void insertOrganization(String slug, String currency) {
        jdbc.sql("""
                insert into organization (name, slug, default_currency, default_locale, time_zone)
                values ('Other', :slug, :currency, 'en', 'UTC')
                """)
            .param("slug", slug)
            .param("currency", currency)
            .update();
    }
}
