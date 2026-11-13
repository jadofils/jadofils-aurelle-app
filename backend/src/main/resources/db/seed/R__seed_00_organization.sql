-- Test seed: organization + shop_setting (contracts/data-model/01-identity-access.md, 09-reporting-settings.md).
-- Loaded only by the `seed` profile group (dev, test, testing, staging). NEVER in production.
-- Idempotent; fixed UUIDs are documented in the data-model docs. Contacts are fake (example.test, Ofcom drama range).

-- 1. Default shop (row created by V0001/V0002): add fake public contacts so clients have something to show.
update organization
   set support_email = 'hello@example.test',
       support_phone = '+447700900001'
 where slug = 'aurelle'
   and support_email is null
   and support_phone is null;

-- 2. Every optional field filled (Rwanda, RWF has no minor unit: amounts are whole francs).
insert into organization (id, name, slug, default_currency, default_locale, time_zone,
                          support_email, support_phone, tax_id)
values ('01970000-0000-7000-8000-000000000102', 'Aurelle Kids Kigali', 'aurelle-kids', 'RWF', 'en-RW',
        'Africa/Kigali', 'kids@example.test', '+447700900002', 'TEST-TIN-000102')
on conflict (id) do nothing;

-- 3. Only required fields (Ghana).
insert into organization (id, name, slug, default_currency, default_locale, time_zone)
values ('01970000-0000-7000-8000-000000000103', 'Aurelle Accra', 'aurelle-accra', 'GHS', 'en-GH', 'Africa/Accra')
on conflict (id) do nothing;

-- 4. Archived organization (edge case: must never be served as the default shop).
insert into organization (id, name, slug, default_currency, default_locale, time_zone, archived_at)
values ('01970000-0000-7000-8000-000000000104', 'Aurelle Pop-up 2025', 'aurelle-popup-2025', 'USD', 'en', 'UTC',
        timestamptz '2026-01-31 18:00:00+00')
on conflict (id) do nothing;

-- shop_setting: the default row exists from V0002; three more covering all-fields, required-only and boundaries.
insert into shop_setting (id, organization_id, shop_name, currency, currency_display_symbol,
                          default_low_stock_threshold, free_shipping_threshold_minor, return_window_days,
                          tagline, footer_text, social_links, order_number_prefix,
                          guest_checkout_enabled, reviews_require_moderation)
values ('01970000-0000-7000-8000-000000000202', '01970000-0000-7000-8000-000000000102',
        'Aurelle Kids', 'RWF', 'FRw', 3, 50000, 14,
        'Little pieces, big adventures.', 'Made in Kigali. Washes well, wears longer.',
        '{"instagram": "https://instagram.example.test/aurellekids"}'::jsonb, 'AK-', true, false)
on conflict (id) do nothing;

insert into shop_setting (id, organization_id, shop_name, currency)
values ('01970000-0000-7000-8000-000000000203', '01970000-0000-7000-8000-000000000103', 'Aurelle Accra', 'GHS')
on conflict (id) do nothing;

-- Boundaries: free shipping on every order (threshold 0), no returns window, guest checkout off.
insert into shop_setting (id, organization_id, shop_name, currency, default_low_stock_threshold,
                          free_shipping_threshold_minor, return_window_days, guest_checkout_enabled,
                          order_number_prefix)
values ('01970000-0000-7000-8000-000000000204', '01970000-0000-7000-8000-000000000104',
        'Aurelle Pop-up', 'USD', 0, 0, 0, false, 'POP-')
on conflict (id) do nothing;
