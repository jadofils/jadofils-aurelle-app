# Phase 9 — Reporting and shop settings (status: draft)

Modules: `reporting`, `settings`. Shop settings are needed from Phase 2 (currency, low stock) — create the table early and extend it.

## shop_setting (one row per organization; location overrides where noted)
| Field | Type | Req | Purpose |
| --- | --- | --- | --- |
| `shop_name` | text | Y | prototype `shopName` |
| `currency` | char(3) | Y | prototype `currency` ("$" becomes ISO code + display symbol) |
| `currency_display_symbol` | text | - | |
| `default_low_stock_threshold` | int | Y | prototype `lowStock` = 5 |
| `free_shipping_threshold_minor` | bigint | - | prototype `freeShip` = 150 |
| `return_window_days` | int | Y | "Easy 30-day returns" |
| `tagline` / `footer_text` | text | - | "Made to be worn on repeat." |
| `social_links` | jsonb | - | |
| `order_number_prefix` | text | Y | "AU-" |
| `guest_checkout_enabled` | boolean | Y | |
| `reviews_require_moderation` | boolean | Y | |

## Read models (documented freshness required for each)
- `daily_sales` (date, location, orders, units, revenue, discounts, shipping, COGS) — refreshed after commit via events or every 5 min; dashboard shows "as of".
- `product_performance` (product, period, units, revenue, margin, return rate, views later).
- `category_trend` (category, this week vs last).
- Profit and cash statements are computed from the ledger on demand within bounded date ranges (no stale aggregates for financial decisions).

## export_job
`type` (`ORDERS_CSV`,`EXPENSES_CSV`,`STOCK_CSV`,`SALES_REPORT_CSV`), `parameters` jsonb, `status` (`QUEUED`,`RUNNING`,`DONE`,`FAILED`,`EXPIRED`), `requested_by`, `result_media_id` (-), `row_count` (-), `expires_at`, `error` (-).
