# Aurelle backend

Java 21 · Spring Boot 4.1.2 · Spring GraphQL · Spring Security · PostgreSQL · Flyway · Spring Modulith 2.1.2.

## Build and test

Requires JDK 21 and a running Docker daemon (integration tests start PostgreSQL with Testcontainers).

```sh
./mvnw verify          # Windows: mvnw.cmd verify
```

## Run locally

```sh
export AURELLE_DB_PASSWORD=<choose-a-local-password>
docker compose up -d
./mvnw spring-boot:run
curl http://localhost:8080/actuator/health/readiness
```

| Variable | Default | Purpose |
| --- | --- | --- |
| `AURELLE_DB_URL` | `jdbc:postgresql://localhost:5432/aurelle` | JDBC URL |
| `AURELLE_DB_USER` / `AURELLE_DB_PASSWORD` | `aurelle` / *(none)* | DB credentials. No password is committed |
| `AURELLE_DB_POOL_MAX` / `AURELLE_DB_POOL_MIN_IDLE` | `10` / `2` | HikariCP bounds per instance |
| `AURELLE_DB_STATEMENT_TIMEOUT_MS` / `AURELLE_DB_LOCK_TIMEOUT_MS` / `AURELLE_DB_IDLE_TX_TIMEOUT_MS` | `5000` / `2000` / `15000` | PostgreSQL session guards |
| `SPRING_PROFILES_ACTIVE=prod` | — | JSON (ECS) structured logs |

## Current state

- Security is deny-by-default: only `/actuator/health`, `/actuator/health/liveness` and `/actuator/health/readiness` are public. Authentication arrives with the identity slice (decision D5).
- There is no GraphQL schema yet, so `/graphql` is not exposed. The first schema comes with depth/complexity limits.
- Migrations go in `src/main/resources/db/migration` (`V<n>__<description>.sql`).
- Modules are top-level packages under `com.aurelle`. `ModularityTests` enforces the boundaries.
