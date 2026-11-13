# Decision log

Status values: **Decided** · **Proposed** (default used in code; easy to change) · **Open** (blocked on product/ops input; code uses interfaces or config only).

| # | Topic | Status | Decision / current default | Notes |
| --- | --- | --- | --- | --- |
| D1 | Backend build tool | Proposed | Maven, with the Maven wrapper | Maven 3.9 is installed locally; Gradle is not |
| D2 | Spring Boot line | Decided | 4.1.2 (latest GA, Nov 2026) + Spring Modulith 2.1.2 | 4.2 is still RC. Pinned in `backend/pom.xml` |
| D3 | Schema migrations | Proposed | Flyway (only) | The rules say pick one. Liquibase not used |
| D4 | Persistence style | Open | Decide in slice 2: Spring Data JDBC + `JdbcClient` (leaning) vs JPA | JDBC favours the explicit SQL, conditional updates and locking the rules need. Only `spring-boot-starter-jdbc` is on the classpath for now |
| D5 | Authentication mechanism | Open | Decide in slice 1 (server sessions + CSRF for web vs short-lived tokens for Android) | Until then every request except health probes is denied |
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
