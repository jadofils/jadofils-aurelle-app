#!/usr/bin/env bash
# Container smoke test for the images built in CI. Images that were not built are skipped.
# Usage: smoke-images.sh <tag>
set -euo pipefail
tag="${1:?usage: smoke-images.sh <tag>}"
backend="${BACKEND_IMAGE:?}:$tag"
frontend="${FRONTEND_IMAGE:?}:$tag"
net="aurelle-smoke-$$"
db_password="$(head -c 24 /dev/urandom | base64 | tr -dc 'A-Za-z0-9')"

cleanup() { docker rm -f smoke-db smoke-backend smoke-web >/dev/null 2>&1 || true; docker network rm "$net" >/dev/null 2>&1 || true; }
trap cleanup EXIT
docker network create "$net" >/dev/null

if docker image inspect "$backend" >/dev/null 2>&1; then
  echo "== backend: $backend"
  user="$(docker image inspect -f '{{.Config.User}}' "$backend")"
  if [[ -z "$user" ]] || [[ "$user" = "root" ]] || [[ "$user" = "0" ]]; then echo "::error::backend image runs as root"; exit 1; fi

  docker run -d --name smoke-db --network "$net" -e POSTGRES_DB=aurelle -e POSTGRES_USER=aurelle \
    -e POSTGRES_PASSWORD="$db_password" postgres:18.7-alpine >/dev/null
  for _ in $(seq 1 30); do docker exec smoke-db pg_isready -U aurelle >/dev/null 2>&1 && break; sleep 2; done

  docker run -d --name smoke-backend --network "$net" -p 18080:8080 \
    -e AURELLE_DB_URL=jdbc:postgresql://smoke-db:5432/aurelle -e AURELLE_DB_USER=aurelle \
    -e AURELLE_DB_PASSWORD="$db_password" "$backend" >/dev/null

  ok=false
  for _ in $(seq 1 30); do
    if curl -fsS http://localhost:18080/actuator/health/readiness | grep -q '"UP"'; then ok=true; break; fi
    sleep 2
  done
  if [[ "$ok" != true ]]; then docker logs smoke-backend | tail -100; echo "::error::backend not ready within 60 s"; exit 1; fi
  echo "backend readiness UP"

  # First public GraphQL operation (phase 0 contract). Skipped until the schema is served.
  code="$(curl -s -o /tmp/gql.json -w '%{http_code}' -H 'Content-Type: application/json' \
    -d '{"query":"{ serverInfo { apiVersion } }"}' http://localhost:18080/graphql || true)"
  if [[ "$code" = "200" ]] && grep -q apiVersion /tmp/gql.json; then echo "GraphQL serverInfo OK"
  else echo "::warning::GraphQL serverInfo not available yet (HTTP $code)"; fi
else
  echo "::warning::backend image not built; backend smoke skipped"
fi

if docker image inspect "$frontend" >/dev/null 2>&1; then
  echo "== frontend: $frontend"
  docker run -d --name smoke-web -p 18081:8080 "$frontend" >/dev/null
  ok=false
  for _ in $(seq 1 15); do curl -fsS http://localhost:18081/ -o /tmp/index.html && ok=true && break; sleep 2; done
  if [[ "$ok" != true ]]; then docker logs smoke-web | tail -50; echo "::error::frontend did not serve / (expected port 8080)"; exit 1; fi
  headers="$(curl -sI http://localhost:18081/)"
  for h in x-content-type-options content-security-policy referrer-policy; do
    echo "$headers" | grep -qi "^$h:" || { echo "::error::frontend missing header $h"; exit 1; }
  done
  echo "frontend serves / with security headers"
else
  echo "::warning::frontend image not built; frontend smoke skipped"
fi
