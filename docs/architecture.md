# Aurelle — discovery and architecture plan

Status: initial plan, recorded before scaffolding (per `PROMPT_FOR_CLAUDE.md`). Open choices live in [decisions.md](decisions.md).

## 1. Prototype discovery (`../aurelle`)

The prototype is a browser-only HTML/CSS/JS demo. All state is in `localStorage` (`aurelle_db_v1`); nothing in it is authoritative.

**Roles in the prototype**
| Prototype role | Pages it can open (`ACCESS` in `js/app.js`) |
| --- | --- |
| Guest | browse, search, shopping assistant, bag, checkout (account optional) |
| Customer | the above + profile, order history/tracking, loyalty points |
| Owner | dash, notifications, products, stock, orders, promos, customers, expenses, reports, team, settings |
| Manager | everything Owner has except team and settings |
| Packer | orders, stock |

These are a **starting point only**. In production these become authorization *data* (roles, grants, scopes), not Java enums.

**Entities seen:** product (type, category, audience men/women/boys/girls/kids, price, cost, tag), colour variants, sizes, stock per colour×size, product photos, customer, staff user, order (`Placed → Packed → Shipped → Delivered`, `Returned`), promo codes (`WELCOME10`, `FREESHIP`, `BONUS2X`), loyalty points, expenses, other income, clothing purchases (which add stock), notifications, delivery address (country → province/district/sector/cell/village, optional GPS).

**Demo-only (must be replaced, not ported):** client-side login with shared password, client-side role checks, localStorage persistence, in-app "forgot password" inbox, browser-side image background removal, mock paging API in `js/api.js`, in-browser report calculations, money held as JS floats.

**To clarify with the product owner:** currency(ies) and tax rules, supported countries and address hierarchy, payment provider, whether the shop is single- or multi-tenant / multi-location, loyalty-points rules, return/refund policy, image-search scope.

**Visual language:** dark default + light theme, accent `#ff7200` / `#f6aa22`, square corners (circles only for swatches/avatars/round icons), Fraunces (display) + Manrope (UI), 4-px spacing scale, 44-px tap targets, bottom tab bar on phones, one status-pill component.

## 2. Repository layout

```
aurelle-app/
  backend/    Java 21, Spring Boot 4.1 modular monolith (Maven)
  frontend/   Flutter/Dart client for Android + Web (Riverpod)
  docs/       this plan + decision log
```

Backend modules are added as top-level packages under `com.aurelle` only when a vertical slice needs them. Spring Modulith verifies the boundaries in `ModularityTests`.

## 3. Phased vertical slices

Each phase ships end to end: migration, domain, GraphQL, authorization, audit, Flutter screens, tests.

| # | Slice | Depends on | Acceptance criteria (summary) |
| --- | --- | --- | --- |
| 0 | Scaffolding (this change) | — | Backend builds; deny-by-default security; health probe; module-boundary test; Flutter skeleton with tokens/breakpoints |
| 1 | Identity + authorization | 0 | Staff and customer login; roles/permissions/scoped bindings as data; inheritance DAG with cycle rejection; audit of grants; recovery via single-use expiring tokens |
| 2 | Catalog + media | 1 | Products/variants CRUD with optimistic versioning; signed media upload with validation; cursor pagination; public browse without login |
| 3 | Inventory | 2 | On-hand/reserved/movements; no negative stock; concurrent-adjustment tests; `stockChanged` subscription after commit (outbox) |
| 4 | Cart + checkout + orders | 3 | Idempotent checkout; atomic reservation; oversell race tests; order status workflow; guest checkout |
| 5 | Payments | 4, provider decision | Provider port + idempotent callbacks; payment-state transitions in transactions |
| 6 | Finance | 4 | Append-only ledger in minor units; expenses/income/purchases; compensating corrections; reconciliation |
| 7 | Customers + promotions + loyalty | 4 | Customer profiles; promo rules; points ledger |
| 8 | Notifications | 3, 4 | Async after commit; deduplicated delivery state; no PII in broadcasts |
| 9 | Reporting | 6 | Bounded report queries/materialized views with documented freshness; async CSV export |

Cross-cutting work runs alongside every phase: observability (traces, metrics, structured logs), GraphQL depth/complexity limits (added with the first schema in phase 1), load tests against the p95 ≤ 1 s objective, and backup/restore.
