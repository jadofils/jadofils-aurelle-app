# Phase 5 — Payments (status: draft, blocked on D7 provider)

Module: `payments`. Prototype methods: Card, Mobile money, Apple Pay. Add Cash on delivery if the business wants it.

## payment
`order_id`, `method` (`CARD`,`MOBILE_MONEY`,`WALLET`,`CASH_ON_DELIVERY`,`STORE_CREDIT`), `provider` (text), `provider_reference` unique (-), `amount_minor`, `currency`, `status` (`CREATED`,`PENDING`,`AUTHORIZED`,`CAPTURED`,`FAILED`,`CANCELLED`), `failure_code`/`failure_message` (-), `payer_phone_e164` (-, PII, mobile money), `card_brand`/`card_last4` (-; never PAN/CVV), `idempotency_key` unique, `authorized_at`/`captured_at` (-).

## payment_event (append-only, webhook inbox)
`provider`, `provider_event_id` unique, `event_type`, `received_at`, `payload_hash`, `payload` jsonb (redacted), `processed_at` (-), `processing_error` (-). Signature verified before insert.

## refund
`payment_id`, `return_request_id` (-), `amount_minor`, `status`, `provider_reference` (-), `reason`, `requested_by`, `idempotency_key` unique.
