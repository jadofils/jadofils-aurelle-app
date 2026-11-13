package com.aurelle.settings.graphql;

import com.aurelle.settings.application.PublicShopSettings;
import com.aurelle.settings.application.ShopSettingsService;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

/** Public {@code shopSettings} query. No authentication: returns only public settings. */
@Controller
class ShopSettingsController {

    private final ShopSettingsService service;

    ShopSettingsController(ShopSettingsService service) {
        this.service = service;
    }

    @QueryMapping
    PublicShopSettings shopSettings() {
        return service.publicSettings();
    }
}
