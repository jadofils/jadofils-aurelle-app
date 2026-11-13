#!/usr/bin/env bash
# Runs post-deploy checks against an environment.
# Usage: e2e.sh <environment> [smoke]
#   full (default, used for testing): readiness + Playwright E2E (tests/e2e) + k6 smoke (tests/load)
#   smoke (staging, production):      readiness + read-only GraphQL check
# BASE_URL comes from the GitHub environment variable BASE_URL. The suites are built by the quality agent.
set -euo pipefail
env_name="${1:?usage: e2e.sh <environment> [smoke]}"
mode="${2:-full}"
summary="${GITHUB_STEP_SUMMARY:-/dev/null}"

if [[ -z "${BASE_URL:-}" ]]; then
  echo "::warning::BASE_URL is not set for environment '$env_name'. Post-deploy checks skipped."
  echo "### $env_name checks: SKIPPED (no BASE_URL)" >> "$summary"
  exit 0
fi

echo "Readiness: $BASE_URL"
for _ in $(seq 1 30); do
  curl -fsS "$BASE_URL/actuator/health/readiness" | grep -q '"UP"' && break
  sleep 5
done
curl -fsS "$BASE_URL/actuator/health/readiness" | grep -q '"UP"' || { echo "::error::$env_name not ready"; exit 1; }

curl -fsS -H 'Content-Type: application/json' -d '{"query":"{ serverInfo { apiVersion } }"}' \
  "$BASE_URL/graphql" | grep -q apiVersion || { echo "::error::$env_name GraphQL check failed"; exit 1; }

if [[ "$mode" = "full" ]]; then
  if [[ -f tests/e2e/package.json ]]; then
    (cd tests/e2e && npm ci --ignore-scripts && ./node_modules/.bin/playwright install --with-deps chromium && BASE_URL="$BASE_URL" ./node_modules/.bin/playwright test)
  else
    echo "::warning::tests/e2e not present yet (quality agent Phase 0)."
  fi
  if [[ -f tests/load/smoke.js ]]; then
    docker run --rm -i -e BASE_URL="$BASE_URL" -v "$PWD/tests/load:/load" -w /load grafana/k6:0.55.0 run smoke.js
  else
    echo "::warning::tests/load/smoke.js not present yet (quality agent Phase 0)."
  fi
fi
echo "### $env_name checks ($mode): passed" >> "$summary"
