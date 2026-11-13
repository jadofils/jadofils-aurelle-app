# Phase 4 — Cart, checkout, orders, returns (status: draft)

Modules: `cart`, `checkout`, `orders`, `delivery`.

## address (address book; also the shape of address snapshots)
| Field | Type | Req | PII | Purpose |
| --- | --- | --- | --- | --- |
| `customer_profile_id` | uuid | Y | | |
| `label` | text | - | | "Home", "Office", "Mum's place" |
| `recipient_name` | text | Y | PII | |
| `recipient_phone_e164` | text | Y | PII | courier calls |
| `country_code` | char(2) | Y | | |
| `admin_area_1` | text | Y | PII | province/state/region |
| `admin_area_2` | text | Y | PII | district/county/city |
| `admin_area_3` | text | - | PII | sector |
| `admin_area_4` | text | - | PII | cell |
| `locality` | text | - | PII | village/neighbourhood |
| `street_line_1` | text | Y | PII | house number, road |
| `street_line_2` | text | - | PII | apartment, floor |
| `landmark` | text | - | PII | "Opposite the blue kiosk" — critical where street numbers are rare |
| `postal_code` | text | - | PII | |
| `latitude` / `longitude` | numeric(9,6) | - | PII | from "Use my location" |
| `location_accuracy_m` | int | - | | |
| `delivery_instructions` | text | - | PII | "Call on arrival, gate code 12" |
| `is_default_shipping` / `is_default_billing` | boolean | Y | | |

`address_format` (lookup per country): `country_code`, ordered level labels and which are required (Rwanda: Province, District, Sector, Cell, Village). Decision D9.

## shipping_method
`code` (`STANDARD`,`EXPRESS`,`PICKUP`), `name`, `price_minor`, `free_over_minor` (-, prototype 150), `estimated_days_min`/`max`, `cutoff_local_time` (-, "packed the same evening"), `countries` (-), `active`, `sort_order`.

## cart / cart_item
- `cart`: `customer_profile_id` (-), `guest_token_hash` (-), `currency`, `promotion_code` (-), `points_to_redeem` (-), `shipping_method_id` (-), `status` (`ACTIVE`,`CONVERTED`,`ABANDONED`), `expires_at`, `last_activity_at`. Guest cart merges into the account cart on sign-in.
- `cart_item`: `cart_id`, `variant_id`, `quantity` > 0, `unit_price_minor_at_add` (shows "price changed"), `saved_for_later` (boolean), `added_at`. Unique (`cart_id`,`variant_id`,`saved_for_later`).

## customer_order
| Field | Type | Req | PII | Purpose |
| --- | --- | --- | --- | --- |
| `order_number` | text unique | Y | | human "AU-001001" |
| `customer_profile_id` | uuid | Y | | guest buyers get a guest profile |
| `contact_name` / `contact_email` / `contact_phone_e164` | text | Y / - / - | PII | at least one of email/phone |
| `status` | text check (`PLACED`,`PACKED`,`SHIPPED`,`DELIVERED`,`CANCELLED`,`RETURNED`,`PARTIALLY_RETURNED`) | Y | | |
| `payment_status` | text (`PENDING`,`AUTHORIZED`,`PAID`,`PARTIALLY_REFUNDED`,`REFUNDED`,`FAILED`) | Y | | |
| `currency` | char(3) | Y | | |
| `subtotal_minor`, `discount_minor`, `points_credit_minor`, `shipping_minor`, `tax_minor`, `total_minor` | bigint | Y | | CHECK total = subtotal − discount − points + shipping + tax |
| `points_earned` / `points_redeemed` | int | Y | | |
| `promotion_id` | uuid | - | | |
| `shipping_method_id` | uuid | Y | | |
| `shipping_address` | jsonb (address snapshot) | Y | PII | |
| `billing_address` | jsonb | - | PII | |
| `location_id` | uuid | Y | | fulfilling location |
| `channel` | text (`WEB`,`ANDROID`,`STAFF`) | Y | | |
| `is_gift` / `gift_message` | boolean / text | Y / - | PII | gift wrap note, hide prices on packing slip |
| `customer_note` | text | - | PII | |
| `carrier` / `tracking_number` / `tracking_url` | text | - | | |
| `estimated_delivery_date` | date | - | | |
| `placed_at`, `packed_at`, `shipped_at`, `delivered_at`, `cancelled_at` | timestamptz | Y / - | | |
| `cancel_reason` | text | - | | |
| `idempotency_key` | text unique | Y | | duplicate checkout protection |
| `locale` | text | - | | emails in the right language |
| `utm_source` / `utm_campaign` | text | - | | marketing attribution |

## order_line (snapshots)
`order_id`, `variant_id`, `product_id`, `product_name`, `color_name`, `color_hex`, `size_label`, `sku`, `image_media_id` (-), `quantity` > 0, `unit_price_minor`, `unit_cost_minor` (staff only), `discount_minor`, `line_total_minor`, `returned_quantity` (Y, default 0).

## order_status_history (append-only)
`order_id`, `from_status` (-), `to_status`, `actor_id` (-), `note` (-), `occurred_at`. Drives the tracking timeline.

## invoice
`order_id` unique, `invoice_number` unique, `issued_at`, `pdf_media_id` (-).

## return_request
`order_id`, `status` (`REQUESTED`,`APPROVED`,`RECEIVED`,`REFUNDED`,`REJECTED`), `resolution` (`REFUND`,`EXCHANGE`,`STORE_CREDIT`), `requested_at`, `return_window_ends_at` (30-day promise), `customer_comment` (-), `staff_note` (-), lines: `order_line_id`, `quantity`, `reason` (`TOO_SMALL`,`TOO_LARGE`,`NOT_AS_PICTURED`,`DAMAGED`,`CHANGED_MIND`,`OTHER`), `condition` (-), `restock` (boolean), `photo_media_ids` (-). Reasons feed fit insights.
