#!/usr/bin/env bash
# Deploys the images for <sha> to <environment> (testing | staging | production).
# The hosting platform is still an open decision (D8). Until it is chosen, deployment uses a generic
# deploy hook: set the environment secret DEPLOY_WEBHOOK_URL in GitHub (Settings > Environments).
# Most platforms (Render, Railway, Coolify, Dokploy, custom) accept such a hook. Without it,
# the job reports clearly that nothing was deployed.
set -euo pipefail
env_name="${1:?usage: deploy.sh <environment> <sha>}"
sha="${2:?usage: deploy.sh <environment> <sha>}"
summary="${GITHUB_STEP_SUMMARY:-/dev/null}"

if [ "${IMAGES_PUSHED:-false}" != "true" ]; then
  echo "::warning::No images were pushed for $sha (Dockerfiles missing?). Nothing deployed to $env_name."
  echo "### $env_name: NOT DEPLOYED (no images)" >> "$summary"
  exit 0
fi

if [ -z "${DEPLOY_WEBHOOK_URL:-}" ]; then
  echo "::warning::DEPLOY_WEBHOOK_URL is not set for environment '$env_name' (decision D8 open). Nothing deployed."
  echo "### $env_name: NOT DEPLOYED (no deploy target configured)" >> "$summary"
  exit 0
fi

payload=$(printf '{"environment":"%s","sha":"%s","backendImage":"%s:%s","frontendImage":"%s:%s"}' \
  "$env_name" "$sha" "${BACKEND_IMAGE:?}" "$sha" "${FRONTEND_IMAGE:?}" "$sha")
curl -fsS --max-time 60 --retry 3 --retry-delay 5 -X POST -H 'Content-Type: application/json' \
  -d "$payload" "$DEPLOY_WEBHOOK_URL" >/dev/null
echo "Deploy hook accepted for $env_name ($sha)"
echo "### $env_name: deploy requested for \`$sha\`" >> "$summary"
