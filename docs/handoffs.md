# Handoffs

Newest first. Each entry: date, from → to, what is ready, where, how to run/verify, known limits.

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
