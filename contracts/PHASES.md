# Delivery phases

Each phase is a vertical slice delivered by three agents in parallel: **BE** (backend), **FE** (frontend + integration with the backend), **QA** (quality: tests, CI, gates). A phase closes only when every exit criterion holds (see `../.claude/rules/multi-agent-workflow.md`). Live progress: `../docs/progress.md`.

Order of work inside a phase: BE moves the contract to `review` → FE and QA comment (proposals) → BE freezes → BE implements while FE builds UI on mocks generated from the contract → FE integrates against the real backend → QA runs E2E + gates → close.

---

## Phase 0 — Foundations (quality and delivery pipeline)
**BE:** split tests into Surefire `*Test` / Failsafe `*IT` (rename `AurelleApplicationTests` → `AurelleApplicationIT`); add JaCoCo (merged unit+IT report, `check` rule ≥ 0.95 line / 0.90 branch); package `../contracts/graphql` as `classpath:graphql/`; `common` GraphQL scalars (`DateTime`, `Date`, `Long`), error mapping, depth/complexity limits; multi-stage `backend/Dockerfile` (non-root); outbox table skeleton decision.
**FE:** generate `android/` and `web/`; routing (go_router) with guest/customer/staff shells; GraphQL client (HTTP + `graphql-transport-ws`) behind repositories; code generation from `contracts/graphql`; shared component library from the prototype (buttons, pill, fields, cards, sheets, table→cards, skeletons, empty/error states); bundle Fraunces + Manrope; golden-test harness; `frontend/Dockerfile` (nginx, security headers).
**QA:** GitHub Actions pipeline (already created: `.github/workflows/pipeline.yml`, `ci/`) made green honestly; pin actions; gitleaks/Semgrep/hadolint/Trivy config; `compose.e2e.yaml` and PR-time E2E; contract-breaking-change job; Playwright + k6 skeletons (`tests/e2e`, `tests/load/smoke.js`); nightly/on-demand load workflow.
**Exit:** pipeline green end to end on the scaffold; coverage ≥ 95 % on both projects; images built, smoke-tested and scanned; a commit on `main` reaches testing and staging (once hosting D8 is configured) and waits for approval before production.

## Phase 1 — Identity and authorization
**BE:** decide D5 (auth transport); sign-up, sign-in, sign-out, email verification, password reset (single-use expiring tokens, no enumeration); customer and staff profiles; organization/location seed; roles, permissions, scoped bindings, inheritance DAG with cycle rejection; seed Owner/Manager/Packer/Customer as **data**; audit events; rate limits on auth.
**FE:** sign-in / sign-up / forgot-password / reset screens; guest mode ("continue without an account"); account page (profile, addresses later); staff shell whose navigation follows the server-reported permissions; team management (owner): add staff, change role, page permissions, activate/deactivate.
**QA:** auth E2E (sign up, sign in, reset, lockout); authorization matrix tests (each seeded role × each operation); brute-force/rate-limit tests; enumeration tests.
**Exit:** customer and staff can sign in on web and Android; permission changes take effect immediately.

## Phase 2 — Catalog and media
**BE:** categories, brands, product types, audiences, size scales, products, colours, variants (SKU), media (signed upload, validation, front/back/cut-out roles), size guides; public browse/search with cursor pagination and filters (audience, category, type, price, colour, size, in-stock); staff product CRUD with optimistic versioning; cost visible only with permission.
**FE:** storefront home ("Walk the rail", audience tabs, category grids 5 per page), product page (turntable, colour/size pickers, size finder, stock message), search with filters, admin product list/editor with photo upload, wishlist (local for guests until Phase 7 sync).
**QA:** browse/search load test (`load` profile) against ≥ 10 000 products and 100 000 variants; E2E guest browse; upload abuse tests.
**Exit:** public catalog browsing p95 within budget; products manageable by staff.

## Phase 3 — Inventory
**BE:** stock levels per variant × location (on-hand, reserved, available), append-only movements with reasons, adjustments (+1/−1/+10/count), low-stock threshold, outbox + `stockChanged` subscription after commit, back-in-stock subscriptions table.
**FE:** admin stock view (search, low/healthy/category filters, variant grid, quick adjust, history log), live stock on product page via subscription, "Notify me" on sold-out sizes.
**QA:** concurrent adjustment tests, negative-stock prevention, subscription fan-out load test, reconnect tests.
**Exit:** no lost updates under concurrency; live updates delivered within the documented latency objective.

## Phase 4 — Cart, checkout and orders
**BE:** server cart (guest token or user), shipping methods (Standard, Express, free-over threshold), address book with country-specific levels + GPS + landmark, checkout with idempotency key and atomic reservation, order + lines with snapshots, status workflow (Placed → Packed → Shipped → Delivered; Cancelled; Returned) with history, guest order tracking by number + email/phone, invoices, return requests.
**FE:** bag sheet, checkout (delivery details, account optional, promo and points placeholders, shipping choice), order confirmation, order history and tracking timeline, guest tracking, admin orders (filters, status moves, invoice print, packing view for packers).
**QA:** oversell race, duplicate checkout, E2E guest checkout and staff fulfilment, `load` + `spike` on checkout.
**Exit:** zero oversell under the race test; checkout p95 within budget.

## Phase 5 — Payments (blocked on decision D7)
**BE:** payment port, provider adapter once chosen (card / mobile money / wallet / cash on delivery), idempotent webhooks, payment state machine, refunds.
**FE:** payment step and result states, retry payment, refund status on orders.
**QA:** webhook duplication/out-of-order tests, provider sandbox E2E.

## Phase 6 — Finance
**BE:** double-entry ledger (minor units, append-only, compensating corrections), expenses with categories and receipts, other income, suppliers, stock purchases (adds stock + posts ledger in one transaction), cash activity statement, reconciliation with orders/payments.
**FE:** Money pages (income, expenses, purchases, statement), receipt upload, CSV export trigger.
**QA:** ledger idempotency and balance invariants (debits = credits), purchase→stock→ledger E2E.

## Phase 7 — Customers, promotions, loyalty, reviews, wishlist
**BE:** customer CRM (profiles, status, notes, tags, lifetime value), promotions (percent, fixed, free shipping, points multiplier; limits, dates, min subtotal, first order), loyalty ledger and tiers (Silver/Gold/Black), reviews with verified purchase and fit feedback + moderation, server wishlist with alerts.
**FE:** promo entry and points redemption at checkout, loyalty ring and history, reviews (write/read, fit summary), admin customers and promotions, wishlist sync after sign-in.
**QA:** promo abuse tests (limits, stacking), loyalty balance invariants, E2E checkout with promo + points.

## Phase 8 — Notifications
**BE:** in-app notifications for staff and customers, per-channel/category preferences, delivery state with retries, email provider port, push device tokens, back-in-stock and price-drop alerts, `notificationReceived` subscription.
**FE:** admin inbox (read/unread, clear read, unread count in nav), customer notification centre and preferences, push on Android.
**QA:** dedup/retry tests; no PII in broadcast payloads; notification failure never rolls back an order.

## Phase 9 — Reporting
**BE:** dashboard KPIs, sales trends, category trends, product performance, profit (revenue − COGS − expenses), low-stock report; documented freshness for any materialized view; async CSV export jobs.
**FE:** owner/manager dashboard (count-up KPIs, charts), reports pages, export downloads.
**QA:** report correctness against seeded ledgers; report queries within budget at representative volume.

## Phase 10 — Hardening and release
**QA lead:** `stress` and `soak` profiles, security review, restore drill, accessibility audit, Play Store internal testing track, production web deploy checklist.
**BE/FE:** fix findings; performance tuning from measurements.
