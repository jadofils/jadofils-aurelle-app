package com.aurelle.settings.domain;

import com.aurelle.common.Money;
import java.util.Objects;
import java.util.Optional;

/**
 * Shop-wide configuration for one organization ({@code contracts/data-model/09-reporting-settings.md},
 * table {@code shop_setting}). Only the fields used so far are modelled; staff-only fields
 * (low-stock threshold, order prefix, moderation) arrive with the phases that use them.
 *
 * @param shopName                   display name
 * @param currency                   ISO 4217 shop currency
 * @param currencyDisplaySymbol      symbol shown in prices, e.g. {@code $}
 * @param freeShippingThresholdMinor order subtotal (minor units) for free shipping; empty = never free
 * @param returnWindowDays           days a customer may return an item
 * @param tagline                    marketing line
 * @param guestCheckoutEnabled       whether customers can buy without an account
 */
public record ShopSettings(String shopName, String currency, String currencyDisplaySymbol,
        Long freeShippingThresholdMinor, int returnWindowDays, String tagline, boolean guestCheckoutEnabled) {

    public ShopSettings {
        Objects.requireNonNull(shopName, "shopName");
        Objects.requireNonNull(currency, "currency");
        if (freeShippingThresholdMinor != null && freeShippingThresholdMinor < 0) {
            throw new IllegalArgumentException("freeShippingThresholdMinor must not be negative");
        }
        if (returnWindowDays < 0) {
            throw new IllegalArgumentException("returnWindowDays must not be negative");
        }
    }

    /** Free-shipping threshold as money in the shop currency, if one is set. */
    public Optional<Money> freeShippingThreshold() {
        return Optional.ofNullable(freeShippingThresholdMinor).map(amount -> Money.of(amount, currency));
    }
}
