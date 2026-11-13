# Events and subscriptions (status: draft, phase 0)

## Transport
GraphQL subscriptions over WebSocket using the `graphql-transport-ws` protocol at `/graphql`. The handshake is authenticated (`connection_init` payload or the session cookie per decision D5). Each subscription is authorized at subscribe time **and** for every event (permissions may be revoked while connected).

## Delivery guarantees
- Events are published only after the transaction commits, via a transactional outbox (`outbox_event` table) and an idempotent relay.
- At-least-once delivery: clients deduplicate by `eventId`. Ordering is guaranteed per `aggregateId` only.
- Payloads are **minimal projections** with no personal data; clients refetch details through authorized queries.
- On reconnect, clients refetch the current state of what they display (no replay guarantee to clients).

## Envelope (outbox, internal)
| Field | Type | Notes |
| --- | --- | --- |
| `event_id` | UUID | dedup key |
| `event_type` | text | e.g. `inventory.stock_changed.v1` (versioned) |
| `aggregate_type` / `aggregate_id` | text / UUID | ordering key |
| `scope` | jsonb | organization/location used for authorization |
| `payload` | jsonb | minimal projection |
| `occurred_at` / `published_at` | timestamptz | `published_at` null until relayed |
| `attempts` / `last_error` | int / text | relay retries |

## Planned subscriptions
| Subscription | Phase | Who may subscribe | Payload |
| --- | --- | --- | --- |
| `stockChanged(productId: ID!)` | 3 | anyone (public availability only: `available`, `lowStock`, `soldOut` per variant) | variant id + availability state, **not** exact counts for customers |
| `stockLevelChanged(locationId: ID)` | 3 | `inventory.stock.read` | variant id, on-hand, reserved, available |
| `orderStatusChanged(orderId: ID!)` | 4 | order owner, or `orders.order.read` | order id, new status, timestamp |
| `ordersFeed` | 4 | `orders.order.read` | order id, status, placedAt |
| `notificationReceived` | 8 | the recipient | notification id, category, unread count |
