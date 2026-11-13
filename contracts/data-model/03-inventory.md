# Phase 3 — Inventory (status: draft)

Module: `inventory`. Availability = `on_hand - reserved`.

## stock_level
| Field | Type | Req | Purpose |
| --- | --- | --- | --- |
| `variant_id` / `location_id` | uuid | Y | unique together |
| `on_hand` | int ≥ 0 | Y | |
| `reserved` | int ≥ 0, ≤ `on_hand` | Y | checkout holds |
| `low_stock_threshold` | int | - | overrides the shop default (prototype `settings.lowStock` = 5) |
| `reorder_point` / `reorder_quantity` | int | - | purchase suggestions |
| `bin_location` | text | - | "Shelf B3" for packers |
| `last_counted_at` | timestamptz | - | stock-take |
| `version` | bigint | Y | |

## stock_movement (append-only)
`variant_id`, `location_id`, `delta` (≠ 0), `reason` (`PURCHASE_RECEIPT`,`SALE`,`RETURN_RESTOCK`,`ADJUSTMENT`,`COUNT_CORRECTION`,`DAMAGE`,`LOSS`,`TRANSFER_IN`,`TRANSFER_OUT`,`INITIAL`), `reference_type`/`reference_id` (-, order/purchase/transfer), `note` (-), `actor_id` (-), `occurred_at`, `idempotency_key` unique (-), `on_hand_after` (Y, for the history log). Prototype `restockLog`.

## stock_reservation
`variant_id`, `location_id`, `quantity` > 0, `cart_id`/`order_id` (one required), `status` (`HELD`,`CONSUMED`,`RELEASED`,`EXPIRED`), `expires_at`. Expired holds are released by a bounded job.

## stock_transfer (optional, multi-location)
`from_location_id`, `to_location_id`, `status`, lines (`variant_id`, `quantity`).

## back_in_stock_request
`variant_id` (Y), `customer_profile_id` (-) or `email`/`phone` (-, guests), `created_at`, `notified_at` (-), `cancelled_at` (-). Unique open request per contact + variant.

## outbox_event
Shared infrastructure introduced here; see `../events.md`.
