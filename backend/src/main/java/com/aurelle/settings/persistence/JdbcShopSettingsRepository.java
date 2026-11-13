package com.aurelle.settings.persistence;

import com.aurelle.settings.application.ShopSettingsRepository;
import com.aurelle.settings.domain.ShopSettings;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** {@code shop_setting} table adapter. */
@Repository
class JdbcShopSettingsRepository implements ShopSettingsRepository {

    private final JdbcClient jdbc;

    JdbcShopSettingsRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<ShopSettings> findByOrganizationId(UUID organizationId) {
        return jdbc.sql("""
                select shop_name, currency, currency_display_symbol, free_shipping_threshold_minor,
                       return_window_days, tagline, guest_checkout_enabled
                  from shop_setting
                 where organization_id = :organizationId
                """)
            .param("organizationId", organizationId)
            .query((rs, row) -> new ShopSettings(
                rs.getString("shop_name"),
                rs.getString("currency"),
                rs.getString("currency_display_symbol"),
                rs.getObject("free_shipping_threshold_minor", Long.class),
                rs.getInt("return_window_days"),
                rs.getString("tagline"),
                rs.getBoolean("guest_checkout_enabled")))
            .optional();
    }
}
