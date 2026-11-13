# CI/CD

Workflow: [`.github/workflows/pipeline.yml`](../.github/workflows/pipeline.yml). It runs on every push, pull request and manual dispatch.

```
backend ─┐                          ┌─ (push to main only) ───────────────────────────────────────┐
frontend ┴─ sonar (quality gate) ─┐ │                                                              │
secret-scan ──────────────────────┼─ images (build, smoke, Trivy, push) ─ testing ─ staging ─ production
sast ─────────────────────────────┘                                     (E2E+k6)  (smoke)  (approval + smoke)
```

| Job | What fails it |
| --- | --- |
| backend | compile error, any unit (`*Test`) or integration (`*IT`, Testcontainers) test, coverage < 95 % line / < 90 % branch (`ci/scripts/check-jacoco.sh`) |
| frontend | `dart format` diff, `flutter analyze --fatal-infos`, any test, line coverage < 95 % (`ci/scripts/check-lcov.sh`) |
| secret-scan | any gitleaks finding in history |
| sast | any Semgrep finding (Java, secrets, Dockerfile, GitHub Actions rules) |
| sonar | SonarQube Cloud quality gate failed (`sonar.qualitygate.wait=true`) |
| images | hadolint errors, image smoke test (`ci/scripts/smoke-images.sh`: non-root, readiness, GraphQL, web security headers), Trivy fixable HIGH/CRITICAL |
| deploy-* | deploy hook failure, post-deploy checks (`ci/scripts/e2e.sh`) |

## Environments (GitHub → Settings → Environments)
| Environment | Trigger | Protection | Checks after deploy |
| --- | --- | --- | --- |
| `testing` | every push to `main` that passes CI | none | readiness, GraphQL, Playwright E2E, k6 smoke |
| `staging` | after testing passes | none | readiness + GraphQL smoke |
| `production` | after staging passes | **required reviewer: repository owner**; only `main` or `v*` tags | readiness + GraphQL smoke |

Per environment configure:
- variable `BASE_URL` (e.g. `https://testing.example.com`), used for checks and the environment link;
- secret `DEPLOY_WEBHOOK_URL`, the hosting platform's deploy hook. The hosting choice is decision **D8**. Until it is set, the deploy jobs pass with a visible **NOT DEPLOYED** warning in the run summary.

Images are pushed to GHCR as `ghcr.io/jadofils/aurelle-backend:<sha>` and `ghcr.io/jadofils/aurelle-frontend:<sha>`, only from `main`.

## SonarQube Cloud
- Organization `jadofils`, project `jadofils_jadofils-aurelle-app`, configuration in [`sonar-project.properties`](../sonar-project.properties). A single project covers backend Java and frontend Dart.
- Repository secret `SONAR_TOKEN` is set (never commit it; locally it lives in the git-ignored `.env`).
- **Automatic Analysis must stay off** (Administration → Analysis Method), because the analysis runs from CI. It is ineligible anyway because Dart needs CI-based analysis.
- The project uses the built-in **Sonar way** gate (new code: coverage ≥ 80 %, duplication ≤ 3 %, A ratings, hotspots reviewed). The current plan does not allow assigning a custom gate to the project, so the **95 % line / 90 % branch target is enforced in the backend and frontend jobs**, which fail before Sonar is reached. If the plan is upgraded, create gate "Aurelle 95" (see `.claude/rules/quality-gates-ci.md`) and assign it.

## Required GitHub settings (owner)
- Branch protection on `main`: require pull requests and the status checks `backend`, `frontend`, `secret-scan`, `sast`, `sonar`, `images`.
- Actions → General → Workflow permissions: read by default (the workflow asks for `packages: write` only in the images job).

## Local equivalents
```sh
(cd backend && ./mvnw verify) && bash ci/scripts/check-jacoco.sh backend/target/site/jacoco/jacoco.xml
(cd frontend && flutter analyze --fatal-infos && flutter test --coverage) && bash ci/scripts/check-lcov.sh frontend/coverage/lcov.info
```
