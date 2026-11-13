# Aurelle contracts

The single source of truth shared by the backend, the Flutter client and the test suites. Rules: `../.claude/rules/contracts.md`.

| Path | What it is | Owner |
| --- | --- | --- |
| [PHASES.md](PHASES.md) | Delivery phases, per-agent tasks, exit criteria, contract status | backend (status), all (task rows) |
| [graphql/](graphql/) | GraphQL SDL, one file per module. The backend packages this folder as `classpath:graphql/`; the frontend generates types from it | backend |
| [data-model/](data-model/) | Tables and fields per module: type, required/optional, PII flag, purpose | backend |
| [events.md](events.md) | Subscriptions and outbox event envelopes | backend |
| [errors.md](errors.md) | Error codes (`extensions.code`) and `UserError` codes | backend |
| [CHANGELOG.md](CHANGELOG.md) | Every change to a frozen contract | backend |
| [proposals/](proposals/) | Change requests from frontend/quality agents | anyone |

## Status per module

| File | Phase | Status |
| --- | --- | --- |
| `graphql/00-common.graphqls`, `errors.md` | 0 | review (served by the backend since phase 0) |
| `events.md` | 0 | draft (reviewed with the first subscription, phase 3) |
| `graphql/01-identity.graphqls`, `data-model/01-identity-access.md`, Phase 1 codes in `errors.md` | 1 | review |
| `graphql/02-catalog.graphqls`, `data-model/02-catalog-media.md` | 2 | draft |
| `data-model/03-inventory.md` | 3 | draft (SDL written by backend at phase start) |
| `data-model/04-cart-checkout-orders.md` | 4 | draft (SDL at phase start) |
| `data-model/05-payments.md` | 5 | draft (SDL at phase start) |
| `data-model/06-finance.md` | 6 | draft (SDL at phase start) |
| `data-model/07-customer-engagement.md` | 7 | draft (SDL at phase start) |
| `data-model/08-notifications.md` | 8 | draft (SDL at phase start) |
| `data-model/09-reporting-settings.md` | 9 | draft (SDL at phase start) |

The seeded drafts were derived from the prototype (`../aurelle/js/*.js`) and extended with fields customers expect. The backend agent reviews, corrects and freezes each phase.
