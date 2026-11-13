-- Module: settings. Data model: contracts/data-model/09-reporting-settings.md (shop_setting).

create table shop_setting (
    id                            uuid        primary key default uuidv7(),
    organization_id               uuid        not null unique references organization (id),
    shop_name                     text        not null check (length(btrim(shop_name)) between 1 and 120),
    currency                      char(3)     not null check (currency ~ '^[A-Z]{3}$'),
    currency_display_symbol       text        check (currency_display_symbol is null or length(currency_display_symbol) between 1 and 8),
    default_low_stock_threshold   integer     not null default 5 check (default_low_stock_threshold >= 0),
    free_shipping_threshold_minor bigint      check (free_shipping_threshold_minor is null or free_shipping_threshold_minor >= 0),
    return_window_days            integer     not null default 30 check (return_window_days between 0 and 365),
    tagline                       text        check (tagline is null or length(tagline) <= 200),
    footer_text                   text        check (footer_text is null or length(footer_text) <= 1000),
    social_links                  jsonb       check (social_links is null or jsonb_typeof(social_links) = 'object'),
    order_number_prefix           text        not null default 'AU-' check (order_number_prefix ~ '^[A-Z0-9-]{1,10}$'),
    guest_checkout_enabled        boolean     not null default true,
    reviews_require_moderation    boolean     not null default true,
    created_at                    timestamptz not null default now(),
    updated_at                    timestamptz not null default now(),
    version                       bigint      not null default 0,
    updated_by                    uuid
);

comment on column shop_setting.updated_by is 'FK to user_account(id) added with the identity module (phase 1).';

-- Defaults from the prototype (js/data.js settings and storefront copy):
-- shopName 'Aurelle', currency '$' (USD), lowStock 5, freeShip 150 (= 15000 minor units),
-- "30-day returns", "Made to be worn on repeat."
insert into shop_setting (organization_id, shop_name, currency, currency_display_symbol,
                          default_low_stock_threshold, free_shipping_threshold_minor,
                          return_window_days, tagline)
select id, 'Aurelle', 'USD', '$', 5, 15000, 30, 'Made to be worn on repeat.'
  from organization
 where slug = 'aurelle';
