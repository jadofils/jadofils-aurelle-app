# CI/CD

Workflow: [`.github/workflows/pipeline.yml`](../.github/workflows/pipeline.yml). It runs on every push, pull request and manual dispatch.

## Branches and environments

| Branch | Role | Deploys to | Gets changes from (pull request only) |
| --- | --- | --- | --- |
| `testing` | development / integration | **testing** environment | `feature/*`, `fix/*`, `chore/*` (cut from `testing`), `hotfix/*` back-ports |
| `staging` | release candidate | **staging** environment | `testing` only |
| `main` | production | **production** environment (owner approval) | `staging` only, or `hotfix/*` for urgent fixes |

```
feature/* | fix/* | chore/* ──PR──▶ testing ──PR──▶ staging ──PR──▶ main
                     │               │              │
                 testing env     staging env   production env (approval)
```

- **Work branches** are always created from `testing` and named `feature/<topic>` (new behaviour), `fix/<topic>` (bug fix) or `chore/<topic>` (tooling, CI, docs, dependencies, refactoring), lowercase with `-`, e.g. `feature/identity-sign-in`, `fix/checkout-rounding`, `chore/pin-actions`. `hotfix/<topic>` is only for urgent production fixes cut from `main`.
- The `promotion-guard` job fails any PR that breaks this order or naming. A `hotfix/*` merged into `main` must also be merged into `testing` (same branch, second PR) so the fix flows forward again.
- Every push and PR runs the full CI below. Pushes to `testing`, `staging` and `main` additionally push images and deploy to the matching environment. Runs on those three branches are never cancelled mid-deploy.

```
backend ─┐
frontend ┴─ sonar (quality gate) ─┐
secret-scan ──────────────────────┼─ images (build, smoke, Trivy) ─┬─ push to testing → deploy testing + E2E + k6 smoke
sast ─────────────────────────────┘                                ├─ push to staging → deploy staging + E2E + k6 smoke
promotion-guard (PRs only)                                         └─ push to main    → approval → deploy production + smoke
```

| Job | What fails it |
| --- | --- |
| backend | compile error, any unit (`*Test`) or integration (`*IT`, Testcontainers) test, coverage < 95 % line / < 90 % branch (`ci/scripts/check-jacoco.sh`) |
| frontend | missing `pubspec.lock`, `dart format` diff, `flutter analyze --fatal-infos`, any test, line coverage < 95 % (`ci/scripts/check-lcov.sh`) |
| secret-scan | any gitleaks finding in history |
| sast | any Semgrep finding (Java, secrets, Dockerfile, GitHub Actions rules) |
| sonar | SonarQube Cloud quality gate failed (`sonar.qualitygate.wait=true`) |
| promotion-guard | PR source branch not allowed for its target |
| images | hadolint errors, image smoke test (`ci/scripts/smoke-images.sh`: non-root, readiness, GraphQL, web security headers), Trivy fixable HIGH/CRITICAL |
| deploy-* | deploy hook failure, post-deploy checks (`ci/scripts/e2e.sh`) |

## Environments (GitHub → Settings → Environments)
| Environment | Allowed branch | Protection | Checks after deploy |
| --- | --- | --- | --- |
| `testing` | `testing` | none | readiness, GraphQL, Playwright E2E, k6 smoke |
| `staging` | `staging` | none | readiness, GraphQL, Playwright E2E, k6 smoke |
| `production` | `main`, `v*` tags | **required reviewer: repository owner** | readiness + GraphQL smoke |

Per environment configure:
- variable `BASE_URL` (e.g. `https://testing.example.com`), used for checks and the environment link;
- secret `DEPLOY_WEBHOOK_URL`, the hosting platform's deploy hook. The hosting choice is decision **D8**. Until it is set, the deploy jobs pass with a visible **NOT DEPLOYED** warning in the run summary.

Images are pushed to GHCR as `ghcr.io/jadofils/aurelle-backend:<sha>` and `ghcr.io/jadofils/aurelle-frontend:<sha>` from `testing`, `staging` and `main`.

## SonarQube Cloud
- Organization `jadofils`, project `jadofils_jadofils-aurelle-app`, configuration in [`sonar-project.properties`](../sonar-project.properties). A single project covers the backend (Maven, Java 21) and the frontend (Flutter, Dart). Scope: production code `backend/src/main`, `frontend/lib`, `ci/`, `.github/`, Dockerfiles; test code `backend/src/test`, `frontend/test`, `frontend/integration_test`, `tests/`; excluded: build output, Flutter platform folders, generated code, `docs/`, `contracts/`.
- Branches: `main`, `staging` and `testing` are long-lived in SonarQube Cloud (each has its own history and new-code baseline); `feature/*`, `fix/*`, `chore/*` branches and PRs are short-lived and compared with their target.
- Repository secret `SONAR_TOKEN` is set (never commit it; locally it lives in the git-ignored `.env`).
- **Automatic Analysis must stay off** (Administration → Analysis Method), because the analysis runs from CI. It is ineligible anyway because Dart needs CI-based analysis.
- The project uses the built-in **Sonar way** gate (new code: coverage ≥ 80 %, duplication ≤ 3 %, A ratings, hotspots reviewed). The current plan does not allow assigning a custom gate to the project, so the **95 % line / 90 % branch target is enforced in the backend and frontend jobs**, which fail before Sonar is reached. If the plan is upgraded, create gate "Aurelle 95" (see `.claude/rules/quality-gates-ci.md`) and assign it.

## Required GitHub settings (owner)
- Branch protection on `testing`, `staging` and `main`: require pull requests and the status checks `backend`, `frontend`, `secret-scan`, `sast`, `sonar`, `images`, `promotion-guard`; block force pushes and deletion.
- Actions → General → Workflow permissions: read by default (the workflow asks for `packages: write` only in the images job).

## Local equivalents
```sh
(cd backend && ./mvnw verify) && bash ci/scripts/check-jacoco.sh backend/target/site/jacoco/jacoco.xml
(cd frontend && flutter analyze --fatal-infos && flutter test --coverage) && bash ci/scripts/check-lcov.sh frontend/coverage/lcov.info
```
