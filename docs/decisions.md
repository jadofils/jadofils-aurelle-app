# Decision log

Status values: **Decided** · **Proposed** (default used in code; easy to change) · **Open** (blocked on product/ops input; code uses interfaces or config only).

| # | Topic | Status | Decision / current default | Notes |
| --- | --- | --- | --- | --- |
| D1 | Backend build tool | Proposed | Maven, with the Maven wrapper | Maven 3.9 is installed locally; Gradle is not |
| D2 | Spring Boot line | Decided | 4.1.2 (latest GA, Nov 2026) + Spring Modulith 2.1.2 | 4.2 is still RC. Pinned in `backend/pom.xml` |
| D3 | Schema migrations | Proposed | Flyway (only) | The rules say pick one. Liquibase not used |
| D4 | Persistence style | Decided | Spring `JdbcClient` adapters behind repository ports (explicit SQL); Spring Data JDBC allowed for simple aggregates; no JPA | Explicit SQL suits conditional updates, row locks and `EXPLAIN` review required by the database rules. Decided 2026-11-13 (backend) |
| D5 | Authentication mechanism | Decided | Opaque server-side sessions (`auth_session`): access token 15 min, refresh token 30 days rotating with reuse detection. Browsers get HttpOnly/Secure/SameSite=Strict cookies and never see tokens; non-browser clients (no `Origin` header, i.e. Android) get bearer tokens in `AuthPayload`; WebSocket auth via `connection_init`. Cookie requests to `/graphql` must carry an allowed `Origin` (CSRF). Passwords: Argon2id | Server-side sessions make revocation and permission changes immediate (phase 1 exit criterion); no JWT key management. Contract: `contracts/graphql/01-identity.graphqls`. Decided 2026-11-13 (backend) |
| D6 | Realtime broker for multiple instances | Open | Single-instance in-memory delivery for dev only. Outbox from slice 3 | Candidates: PostgreSQL LISTEN/NOTIFY relay, Redis, RabbitMQ, Kafka |
| D7 | Payment provider | Open | Port interface only | No invented integrations |
| D8 | Cloud / deployment | Open | Container images in GHCR, health probes on `/actuator/health/{liveness,readiness}`; deploy via per-environment `DEPLOY_WEBHOOK_URL` + `BASE_URL` until a host is chosen | Three environments exist in GitHub: testing, staging, production |
| D9 | Countries / address hierarchy / GIS | Open | Free-text + optional coordinates | The prototype implies Rwanda-style province→village, which needs confirming |
| D10 | Image storage | Open | Storage port + signed upload URL | S3-compatible object storage likely |
| D11 | Tax / accounting policy, currency | Open | Money as integer minor units + ISO-4217 code | |
| D12 | Public SEO surface | Proposed | Separate crawlable landing/catalog site later; authenticated app stays in Flutter | Flutter Web is weak for SEO |
| D13 | Flutter state management | Decided | Riverpod (`flutter_riverpod` 3.x) | Required by the project brief |
| D14 | Fonts | Proposed | Bundle Fraunces + Manrope as assets (no runtime Google Fonts fetch) | Not bundled yet. The theme falls back to platform fonts |
| D15 | Multi-tenancy | Open | Authorization scopes are modelled generically (tenant/org/location) | Is this one shop or many? Data model seeds one `organization` + one `location` |
| D16 | CI/CD and branches | Decided | GitHub Actions (`.github/workflows/pipeline.yml`). Branches `testing` (dev) → `staging` → `main` (production), promoted by PR; each deploys to its own environment; production needs owner approval; images in GHCR | Owner's choice. See `ci/README.md` |
| D17 | Quality gate | Decided | SonarQube Cloud project `jadofils_jadofils-aurelle-app` on "Sonar way" + CI-enforced ≥ 95 % line / ≥ 90 % branch (backend and frontend) | Plan cannot assign a custom gate; the coverage floor is enforced by `ci/scripts/check-*.sh` |
| D18 | Contract ownership | Decided | `contracts/` is the source of truth; backend packages `contracts/graphql` as its schema; frontend generates types from it | `.claude/rules/contracts.md` |
| D19 | Load testing tool | Proposed | k6 (HTTP + WebSocket), profiles smoke/load/stress/spike/soak | Quality agent may revise |
| D20 | E2E tooling | Proposed | Playwright (web UI with Flutter semantics + API), Flutter `integration_test` for Android | Quality agent confirms |
| D21 | Identifiers | Proposed | UUID v7 primary keys; human order numbers separate | Data-model conventions |
| D22 | Shopping assistant + image search (prototype `assist.js`, `api.js`) | Open | Not scheduled; size finder is in Phase 2 as `suggestSize` | Rule-based vs LLM vs vector search to be decided |
| D23 | PostgreSQL version | Decided | PostgreSQL 18 (pinned `postgres:18.7-alpine` in compose, Testcontainers and smoke tests) | Built-in `uuidv7()` for UUID v7 keys (D21); newest stable major. Hosting must offer PG 18 (D8) |
| D24 | GraphQL scalars | Decided | `DateTime`, `Date`, `Long` implemented in `platform.graphql.Scalars` | graphql-java-extended-scalars has no release for graphql-java 25 (Boot 4.1) |
| D25 | `/graphql` exposure | Decided | Endpoint is public at the HTTP layer; every operation authorizes in its use case/resolver; CSRF not applied while no cookie session exists | Public queries (shop settings, catalog) need no account. Revisit CSRF with D5 |
| D26 | Schema packaging | Decided | `backend/pom.xml` packages `contracts/graphql/*.graphqls` per implemented phase into `classpath:graphql/`; `SchemaMappingIT` fails on unmapped fields | One schema source; a contract file is packaged only when its resolvers exist |
| D27 | GraphQL limits | Decided | Max depth 12, max complexity 500 (field count), HTTP request timeout 10 s; introspection off in `prod` | Configurable via `AURELLE_GRAPHQL_MAX_DEPTH` / `_MAX_COMPLEXITY`; revisit with load tests |
| D28 | E-mail and SMS provider | Open | `MailSender` / `SmsSender` ports; dev and test use a local catcher (Mailpit) and log no tokens; phone verification waits for an SMS provider | Needed for verify-email, password reset and staff invites (phase 1). Product owner to pick a provider (market/country dependent, D9) |
