# Phase 8 — Notifications (status: draft)

Module: `notifications`.

## notification
`recipient_id` (Y, user), `category` (`ORDERS`,`STOCK`,`CUSTOMERS`,`SECURITY`,`MARKETING`,`ACCOUNT`), `title` (Y), `body` (Y, **no unnecessary PII**), `link` (-, in-app route), `resource_type`/`resource_id` (-), `created_at`, `read_at` (-), `archived_at` (-). Staff notifications fan out to recipients who hold the right permission in the right scope (prototype `notifyAdmin`). Keep at most N per user visible (prototype keeps 100); older ones archived.

## notification_preference
`user_account_id`, `category`, `channel` (`IN_APP`,`EMAIL`,`SMS`,`PUSH`), `enabled`. Unique (user, category, channel). Prototype prefs: orders, stock, customers, security. `SECURITY` in-app cannot be disabled.

## notification_delivery
`notification_id`, `channel`, `status` (`PENDING`,`SENT`,`FAILED`,`SUPPRESSED`), `attempts`, `next_attempt_at`, `provider_reference` (-), `last_error` (-), `dedup_key` unique.

## device_token
`user_account_id`, `platform` (`ANDROID`,`WEB`), `token` (secret), `last_seen_at`, `revoked_at` (-).
