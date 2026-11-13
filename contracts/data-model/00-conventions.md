# Data model conventions (status: draft)

Rules: `../../.claude/rules/data-model.md`. Column legend for every table in this folder: **Req** = `Y` NOT NULL / `-` optional; **PII** = personal data (erasure, redaction, restricted access).

## Standard columns (implied on every table unless the table says otherwise)
| Field | Type | Req | Notes |
| --- | --- | --- | --- |
| `id` | uuid (v7) | Y | primary key, never reused |
| `created_at` | timestamptz | Y | default `now()` |
| `updated_at` | timestamptz | Y | edited rows only |
| `version` | bigint | Y | optimistic lock, edited rows only |
| `created_by` / `updated_by` | uuid → `user_account` | - | staff-edited tables only |
| `archived_at` | timestamptz | - | soft archive for business records |

Append-only tables (movements, ledger, loyalty, audit, status history) have `id`, `created_at` (or `occurred_at`) and no `updated_at`/`version`; UPDATE and DELETE are revoked from the application role.

## Prototype field mapping (for reference)
| Prototype | Production |
| --- | --- |
| `product.cat` | `product.category_id` → `category` |
| `product.aud` (`men/women/boys/girls/kids`) | `product.audience` (`MEN/WOMEN/BOYS/GIRLS/KIDS_UNISEX`, plus `UNISEX`) |
| `product.colors[{n,hex}]` | `product_color` |
| `product.sizes` | `size_scale` / `size_value` / `product_size` |
| `product.stock['ci-size']` | `product_variant` + `stock_level` |
| `product.tag` | `product.badge` (+ `product_tag` for search) |
| `product.price/cost` (floats) | `price_minor`/`cost_minor` (integer minor units) |
| `user.role` | `role_binding` (authorization data) |
| `user.permissions` (page keys) | permissions granted through roles/bindings |
| `user.points` | `loyalty_account` + `loyalty_transaction` |
| `user.address` / `addressObj` | `address` (address book) |
| `order.items[{pid,ci,size,qty,price,cost}]` | `order_line` with snapshots |
| `order.code/credit/points` | `promotion_redemption`, `loyalty_transaction` |
| `order.ship/pay` | `shipping_method`, `payment` |
| `restockLog` | `stock_movement` |
| `moneyEntries` (`income`/`purchase`), `expenses` | `other_income`, `stock_purchase`, `expense`, ledger |
| `notifications`, `settings.notificationPrefs` | `notification`, `notification_preference` |
| `settings` (`shopName,currency,lowStock,freeShip`) | `shop_setting` |
| `wishlists`, `carts`, `reviews` | `wishlist_item`, `cart`/`cart_item`, `review` |
