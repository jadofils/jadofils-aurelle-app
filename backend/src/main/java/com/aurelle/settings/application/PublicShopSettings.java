package com.aurelle.settings.application;

import com.aurelle.common.Money;

/**
 * Shop settings safe for anyone, signed in or not (GraphQL {@code ShopSettings}).
 * Staff-only settings are never part of this type.
 */
public record PublicShopSettings(
    String shopName,
    String currency,
    String currencyDisplaySymbol,
    Money freeShippingThreshold,
    int returnWindowDays,
    String tagline,
    String supportEmail,
    String supportPhone,
    boolean guestCheckoutEnabled) {
}
