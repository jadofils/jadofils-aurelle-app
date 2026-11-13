# Phase 7 — Customers, promotions, loyalty, reviews, wishlist (status: draft)

Modules: `customers`, `promotions`, `loyalty`, `reviews`, `wishlist`.

## customer_note / customer_tag (staff CRM)
- `customer_note`: `customer_profile_id`, `body` (PII), `author_id`, `pinned` (Y).
- `customer_tag`: `customer_profile_id`, `tag` ("VIP", "Wholesale").
Lifetime value, order count and last order date are computed read models, not stored on the profile.

## promotion
| Field | Type | Req | Purpose |
| --- | --- | --- | --- |
| `code` | citext unique | Y | "WELCOME10" |
| `type` | text (`PERCENT`,`FIXED_AMOUNT`,`FREE_SHIPPING`,`POINTS_MULTIPLIER`) | Y | prototype `pct`/`flat`/`ship`/`bonus` |
| `value` | numeric(10,2) / bigint | Y | percent, minor units, or multiplier |
| `min_subtotal_minor` | bigint | Y default 0 | prototype `min` |
| `max_discount_minor` | bigint | - | cap for percent codes |
| `starts_at` / `ends_at` | timestamptz | - | prototype `expires` |
| `usage_limit_total` / `usage_limit_per_customer` | int | - | |
| `first_order_only` | boolean | Y | WELCOME codes |
| `applies_to` | text (`ALL`,`CATEGORIES`,`PRODUCTS`,`AUDIENCES`) + `promotion_target` rows | Y | |
| `stackable_with_points` | boolean | Y | |
| `active` | boolean | Y | |
| `description` | text | - | prototype `note`, shown to customers |

`promotion_redemption`: `promotion_id`, `order_id` unique, `customer_profile_id`, `discount_minor`, `redeemed_at`.

## loyalty
- `loyalty_program_setting`: `earn_points_per_currency_unit` (1), `points_per_currency_unit_redeemed` (20 = 1 unit), `points_expiry_months` (-).
- `loyalty_tier`: `key` (`SILVER`,`GOLD`,`BLACK`), `name`, `min_points` (0/500/1500), `perks` (text), `sort_order`.
- `loyalty_account`: `customer_profile_id` unique, `balance` ≥ 0, `lifetime_points`, `tier_id`, `version`.
- `loyalty_transaction` (append-only): `loyalty_account_id`, `type` (`EARN`,`REDEEM`,`EXPIRE`,`ADJUST`,`REVERSE`), `points` (signed), `order_id` (-), `reason` (-), `actor_id` (-), `expires_at` (-), `idempotency_key` unique.

## review
`product_id`, `customer_profile_id`, `order_line_id` (-; `verified_purchase` derived), `rating` 1–5, `title` (-), `body` (Y), `fit_feedback` (`RUNS_SMALL`,`TRUE_TO_SIZE`,`RUNS_LARGE`) (-), `size_purchased` (-), `height_cm` (-, opt-in), `photo_media_ids` (-), `status` (`PENDING`,`PUBLISHED`,`HIDDEN`), `display_name` (Y, "Sade W."), `helpful_count` (Y), `staff_reply` / `staff_replied_at` (-). One review per customer per product.

## wishlist_item
`customer_profile_id` (-) or `guest_token_hash` (-), `product_id` (Y), `color_id` (-), `size_value_id` (-), `added_at`, `notify_price_drop` (Y default true), `notify_back_in_stock` (Y default true). Guest wishlist merges on sign-in.
