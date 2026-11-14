# Handoffs

Newest first. Each entry: date, from → to, what is ready, where, how to run/verify, known limits.

## 2026-11-14 — backend → frontend, quality: Phase 2 catalog contract **frozen**
- Frozen: `contracts/graphql/02-catalog.graphqls`, `contracts/data-model/02-catalog-media.md`, Phase 2 codes in `errors.md`. Generate client types from it; changes from now on go through `contracts/proposals/`.
- Last change before the freeze: `SizeSuggestion.confidence` is the enum `SizeConfidence` (`HIGH`, `MEDIUM`, `LOW`).
- **No stock in Phase 2.** `Product`/`ProductVariant` have no stock fields until Phase 3 (`03-inventory.graphqls`: `stockState`, `ProductColor.available`, `inStockOnly`). Do not build sold-out greying, "Only N left" or in-stock filters against Phase 2; show every active colour and size as selectable and add the stock UI when Phase 3 is frozen.
- Implementation in progress on `feature/catalog-media`; I'll add a handoff entry when the API is live.

## 2026-11-14 — backend → frontend, quality: Phase 2 catalog contract in `review` (PR `feature/catalog-contract`)
- **Read:** `contracts/graphql/02-catalog.graphqls`, `contracts/data-model/02-catalog-media.md`, Phase 2 codes in `contracts/errors.md`, decision D10. Comment through `contracts/proposals/`; I freeze after comments or when the coordinator merges this PR.
- **Moved out of Phase 2** (no data behind them yet): `stockState` on products/variants, `ProductColor.available` and the `inStockOnly` filter are now in `03-inventory.graphqls`; `ratingAverage`/`ratingCount` and sort `RATING` are in `07-engagement.graphqls`. Build the product page's stock message against the Phase 3 fields.
- **Added:** `category(slug)`, `sizeScales`, `sizeGuides`, `mediaStatus` (poll after upload), `upsertBrand`; `Product.tags/season/countryOfOrigin/sizeScale/publishedAt`; `version` on categories and brands; `Image.url(width)` rounds to 160/320/640/1024/1600.
- **Uploads:** `requestMediaUpload` → PUT the bytes to `uploadUrl` with the given headers (≤ 10 MB, jpeg/png/webp/avif) → poll `mediaStatus` until READY → `attachProductMedia` with alt text. Publishing (ACTIVE) needs a PRIMARY photo with alt text.
- **Quality:** the phase exit needs the browse/search load test against ≥ 10 000 products / 100 000 variants; the seeded catalogue (20 prototype products) is for functional tests only.

## 2026-11-13 — backend → frontend, quality: Phase 1 API is live (branch `feature/identity-auth`)
- **Served now** (`serverInfo.contractPhase` = 1): every query and mutation in `contracts/graphql/01-identity.graphqls`. `SchemaMappingIT` proves each field has a resolver.
- **Sign in as seeded users** (dev/test/testing/staging): `owner@`, `manager@`, `packer@example.test` (staff), `amina@example.test` (customer, verified), `kofi@` (inactive), `nia@` (locked), `invited@` (invite pending). Password = the `AURELLE_SEED_PASSWORD` of that environment.
- **Web client:** send requests with the page's `Origin` (browsers do); tokens are never in the body; cookies `__Host-aurelle_at` / `__Host-aurelle_rt` (HttpOnly, SameSite=Strict). Allowed origins: `AURELLE_ALLOWED_ORIGINS` (default `http://localhost:8081`). Plain-http local setups other than localhost need `AURELLE_SECURE_COOKIES=false`.
- **Android client:** no `Origin` header; read `accessToken`/`refreshToken` from `AuthPayload`, send `Authorization: Bearer …`, call `refreshSession(input: {refreshToken})` before `accessTokenExpiresAt` (15 min).
- **Verification e-mails** contain a 6-digit code and a link (`<AURELLE_PUBLIC_WEB_URL>/verify-email?token=…`, `/reset-password?token=…`, `/accept-invite?token=…`); the web app routes must read `token` and call `verifyEmail` / `resetPassword` / `acceptStaffInvite`.
- **Quality:** environments need `AURELLE_SEED_PASSWORD`, `AURELLE_CODE_HMAC_KEY` (random 32+ chars, secret), `AURELLE_ALLOWED_ORIGINS`, `AURELLE_PUBLIC_WEB_URL`, and `AURELLE_MAIL_*` to send e-mail. Rate limits: sign-in 10 per account and 100 per client address per 15 min (`RATE_LIMITED` with `retryAfterSeconds`).
- **Known limits:** breached-password check is a small offline list (full check is a follow-up); SMS verification waits for D28; audit log has no read API yet (`audit.event.read` reserved).

## 2026-11-13 — backend → frontend, quality: Phase 1 contract in `review` (PR `feature/identity-access`)
- **Read:** `contracts/graphql/01-identity.graphqls`, `contracts/data-model/01-identity-access.md`, Phase 1 codes in `contracts/errors.md`, decision D5 in `docs/decisions.md`. Comment through `contracts/proposals/<date>-<topic>.md`; I freeze after your comments (or by the end of the next working session if there are none).
- **Auth (D5), what the client does:** web: nothing special; sessions are HttpOnly cookies, tokens never appear in responses. Android: send no `Origin` header, keep `accessToken`/`refreshToken` from `AuthPayload` in secure storage, send `Authorization: Bearer …`, call `refreshSession` before `accessTokenExpiresAt`, and put `{"authorization":"Bearer …"}` in the WebSocket `connection_init` payload.
- **Changes from the draft:** every mutation takes one `input` (now enforced by `ContractSchemaTest` across all contract files); typed enums (`ClientPlatform`, `ScopeType`, `LocationKind`, `RoleKind`, `PermissionEffect`); `version` on editable types for `expectedVersion`; new `acceptStaffInvite`, `setStaffPermissionOverrides` (prototype per-member page permissions), `archiveRole`, `staffMember(id)`, `locations`; `AccountStatus.PENDING_VERIFICATION` replaced by `INVITED` (customers can shop before verifying e-mail); `refreshToken` → `refreshSession(input)`. Catalog draft: `archiveProduct`, `setProductHidden`, `detachProductMedia` now take inputs; upload permission key is `media.asset.upload`.
- **Navigation:** `viewer.permissions` lists permission keys; the prototype page → key mapping is the catalogue table in the data-model doc.
- **Quality:** seeded accounts for the authorization matrix are listed under "Seed data" in the data-model doc (Owner/Manager/Packer/invited staff + 4 customers incl. locked and inactive). Seeded passwords come from `AURELLE_SEED_PASSWORD` (testing/staging need it).
- **Open:** e-mail/SMS provider (D28). Until chosen, dev/test e-mails go to a local catcher and phone verification is unavailable.

## 2026-11-13 — coordinator → all: branch names and Sonar scope
- **Work branches:** always cut from `origin/testing`, named `feature/<topic>`, `fix/<topic>` or `chore/<topic>` (lowercase, `-` separated). PRs go to `testing`. CI (`promotion-guard`) rejects other names. `ci/github-actions-cicd` and `backend/phase-0` are allowed until merged; use the new names for everything after.
- **SonarQube Cloud:** `main`, `staging`, `testing` are long-lived branches; scope and test paths are explicit in `sonar-project.properties` (backend Maven/Java and frontend Flutter/Dart, plus CI scripts).

## 2026-11-13 — backend → quality, frontend: Phase 0 backend ready (branch `backend/phase-0`, PR to `testing`)
- **Coverage for CI:** `./mvnw -B -ntp verify` now runs Surefire (`*Test`, 60 unit tests) + Failsafe (`*IT`, 18 Testcontainers tests), merges JaCoCo unit + IT data into `backend/target/site/jacoco/jacoco.xml` and fails below 0.95 line / 0.90 branch. Local result: `ci/scripts/check-jacoco.sh` reports line 100 %, branch 100 %.
- **Quality, action needed (files you own):**
  1. `backend/Dockerfile` must be built from the **repository root**, because the schema comes from `contracts/graphql`: change the pipeline step to `docker build -f backend/Dockerfile -t "$BACKEND_IMAGE:${{ github.sha }}" .` (currently `... backend`). A root `.dockerignore` excludes `frontend/`, `.git`, targets and `.env`.
  2. PostgreSQL is now **18** (decision D23; migrations use built-in `uuidv7()`). Change `postgres:17-alpine` to `postgres:18.7-alpine` in `ci/scripts/smoke-images.sh` (and any compose/E2E file). With 17 the backend fails to migrate.
  - Verified locally: hadolint v2.12.0 clean; image runs as UID 10001, readiness UP in ~18 s, Docker HEALTHCHECK `healthy`, `{ serverInfo { apiVersion } }` answers, logs are ECS JSON (prod profile), introspection is off in prod (returns `BAD_REQUEST`).
- **Frontend, first live endpoint:** public queries `serverInfo { apiVersion contractPhase }` and `shopSettings { shopName currency currencyDisplaySymbol freeShippingThreshold { amountMinor currency } returnWindowDays tagline supportEmail supportPhone guestCheckoutEnabled }` at `POST /graphql`. Seed values come from the prototype (Aurelle, USD `$`, free shipping 15000 minor units, 30-day returns). Every error carries `extensions.code` from `contracts/errors.md`; `INTERNAL` also has `extensions.correlationId`. Limits: depth 12, complexity 500 (→ `QUERY_TOO_COMPLEX`).
  - Run it: `cd backend && export AURELLE_DB_PASSWORD=<local> && docker compose up -d && ./mvnw spring-boot:run`, or build the image as above.
- **Contracts:** `00-common.graphqls` and `errors.md` moved to `review`. Comments go to `contracts/proposals/`. I will freeze them together with Phase 1 (next).
- Not in this change: dev seed of prototype products/customers/orders (needs the Phase 2/4 tables), outbox table (decided with the first event, Phase 3), request-size limit and rate limiting (Phase 1 with auth).

## 2026-11-13 — coordinator → all: branch model testing → staging → main
- Long-lived branches: `testing` (dev, deploys to the testing environment), `staging` (deploys to staging), `main` (production, owner approval). Each GitHub environment only accepts its own branch.
- **Open every PR against `testing`**, from a branch named `<role>/<task>` (e.g. `backend/phase-0`, `frontend/design-system`). Base new work on `origin/testing`. Only `testing` → `staging` and `staging` → `main` promotions are allowed (CI job `promotion-guard`); `hotfix/*` may go to `main` and must also be merged into `testing`.
- PR #1 (CI/CD) now targets `testing`.

## 2026-11-13 — coordinator → all: repository moved to jadofils/jadofils-aurelle-app
- All CI/Sonar references now use repo `jadofils/jadofils-aurelle-app` and Sonar project `jadofils_jadofils-aurelle-app` (Sonar way gate). New `SONAR_TOKEN` secret set; environments `testing`, `staging`, `production` recreated (production: owner approval, `main`/`v*` only).
- CI pins Flutter 3.44.0 (Dart 3.12.0), matching the Docker check below.
- **Frontend:** the frontend CI job stays red until `dart format lib test` is applied to `theme.dart`, `tokens.dart`, `window_size.dart`, `test/app_test.dart` (see the next entry).
- **Frontend:** commit `frontend/pubspec.lock`. CI runs `flutter pub get --enforce-lockfile` (SonarQube security rule githubactions:S8550: dependencies must be locked).

## 2026-11-13 — scaffold session → frontend: Flutter checks run in Docker (read-only)
- Toolchain: Docker image `ghcr.io/cirruslabs/flutter:3.44.0` (Flutter 3.44.0, Dart 3.12.0; meets `sdk: ^3.12.0`). Pin this tag in CI rather than `stable`. No Flutter install on the host is needed.
- How to run without writing into `frontend/` (bind-mount read-only, work on a copy; from Git Bash in `aurelle-app/`):
  `MSYS_NO_PATHCONV=1 docker run --rm -v "$(pwd -W)/frontend:/src:ro" ghcr.io/cirruslabs/flutter:3.44.0 bash -c 'cp -r /src /w && cd /w && flutter pub get && flutter analyze --fatal-infos && dart format --output=none --set-exit-if-changed lib test && flutter test --coverage'`
  Drop `:ro` and the copy step to let it write files (e.g. `pubspec.lock`, formatting).
- Results on the current `frontend/` (commit `8ad54ed`):
  - `flutter pub get`: OK (37 deps resolved).
  - `flutter analyze --fatal-infos`: **No issues found**.
  - `flutter test --coverage`: **9/9 passed**. lcov line coverage 54/54 = 100 %. Note: there is no all-files import test yet (rule in `quality-gates-ci.md`).
  - `dart format --set-exit-if-changed lib test`: **FAILS (CI will be red)**. It would reformat `lib/core/design/theme.dart`, `lib/core/design/tokens.dart`, `lib/core/layout/window_size.dart`, `test/app_test.dart`. Fix: run `dart format lib test` in the container (without `:ro`) and commit. Formatting only, no behaviour change. Not applied here because `frontend/**` is owned by the frontend agent.
- Not done: `flutter create --platforms=android,web` (platform folders still missing), no `pubspec.lock` committed yet.

## 2026-11-13 — coordinator → all: CI/CD is GitHub Actions + SonarQube Cloud (not Jenkins)
- Pipeline: `.github/workflows/pipeline.yml`, docs `ci/README.md`, scripts `ci/scripts/`, Sonar config `sonar-project.properties` (single project `jadofils_jadofils-aurelle-app`).
- GitHub: secret `SONAR_TOKEN` set; environments `testing`, `staging`, `production` created (production = owner approval, `main`/`v*` only). Hosting (D8) not chosen: deploy jobs report NOT DEPLOYED until each environment gets `BASE_URL` + `DEPLOY_WEBHOOK_URL`.
- **Backend:** CI fails until `backend/target/site/jacoco/jacoco.xml` (merged unit+IT) exists and shows ≥ 95 % line / ≥ 90 % branch. Also needed: `backend/Dockerfile` (non-root, port 8080, env `AURELLE_DB_*`).
- **Frontend:** CI runs `dart format --set-exit-if-changed lib test`, `flutter analyze --fatal-infos`, `flutter test --coverage`, then requires ≥ 95 % line. Needed: `frontend/Dockerfile` serving on port **8080** with `X-Content-Type-Options`, `Content-Security-Policy`, `Referrer-Policy` headers; a test importing all `lib/` files so coverage is honest.
- **Owner rule:** no AI attribution in commits/PRs (see `CLAUDE.md`).

## 2026-11-13 — coordinator → backend, frontend, quality
Multi-agent setup created: rules in `.claude/rules/` (new: `design-system.md`, `contracts.md`, `data-model.md`, `quality-gates-ci.md`, `multi-agent-workflow.md`; rewritten: `testing.md`), agent briefs in `.claude/agents/`, contracts seeded in `contracts/` (all `draft`), phase plan in `contracts/PHASES.md`, board in `docs/progress.md`. Each agent starts with its Phase 0 tasks. Backend: review and freeze `00-common` and Phase 1 contracts first, because the frontend is waiting on them.
