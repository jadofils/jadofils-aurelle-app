# Aurelle backend

Java 21 · Spring Boot 4.1.2 · Spring GraphQL · Spring Security · PostgreSQL 18 · Flyway · Spring Modulith 2.1.2.

## Build and test

Requires JDK 21 and a running Docker daemon (integration tests start PostgreSQL 18.7 with Testcontainers).

```sh
./mvnw -B -ntp verify          # Windows: mvnw.cmd -B -ntp verify
```

| Suite | Naming | Plugin | Needs |
| --- | --- | --- | --- |
| Unit | `*Test` | Surefire (`test` phase) | nothing (no Spring context, no Docker) |
| Integration | `*IT` (extend `IntegrationTest`) | Failsafe (`integration-test`/`verify`) | Docker |

JaCoCo merges both into `target/site/jacoco/jacoco.xml` and fails the build below **95 % line / 90 % branch** (`coverage.*.minimum` in `pom.xml`, never lower them).

## Run locally

```sh
export AURELLE_DB_PASSWORD=<choose-a-local-password>
docker compose up -d
./mvnw spring-boot:run
curl -s -X POST -H 'Content-Type: application/json' \
  -d '{"query":"{ serverInfo { apiVersion } shopSettings { shopName } }"}' http://localhost:8080/graphql
```

## Container image

Build from the **repository root** (the schema comes from `../contracts/graphql`):

```sh
docker build -f backend/Dockerfile -t aurelle-backend .
```

Non-root (UID 10001), port 8080, `prod` profile (ECS JSON logs, introspection off), Docker `HEALTHCHECK` on `/actuator/health/liveness`.

## Configuration

| Variable | Default | Purpose |
| --- | --- | --- |
| `AURELLE_DB_URL` | `jdbc:postgresql://localhost:5432/aurelle` | JDBC URL |
| `AURELLE_DB_USER` / `AURELLE_DB_PASSWORD` | `aurelle` / *(none)* | DB credentials. No password is committed |
| `AURELLE_DB_POOL_MAX` / `AURELLE_DB_POOL_MIN_IDLE` | `10` / `2` | HikariCP bounds per instance |
| `AURELLE_DB_STATEMENT_TIMEOUT_MS` / `AURELLE_DB_LOCK_TIMEOUT_MS` / `AURELLE_DB_IDLE_TX_TIMEOUT_MS` | `5000` / `2000` / `15000` | PostgreSQL session guards |
| `AURELLE_GRAPHQL_MAX_DEPTH` / `AURELLE_GRAPHQL_MAX_COMPLEXITY` | `12` / `500` | GraphQL limits (`QUERY_TOO_COMPLEX`) |
| `SPRING_PROFILES_ACTIVE=prod` | set in the image | ECS JSON logs, introspection off |

## Structure

Modules are top-level packages under `com.aurelle`; `ModularityTest` (Spring Modulith) enforces their boundaries.

| Module | Owns | Public API |
| --- | --- | --- |
| `common` | shared kernel: `ErrorCode`, `ApplicationException`, `Money` | open module |
| `platform` | GraphQL runtime: scalars, error codes, depth/complexity limits; `serverInfo` | — |
| `organization` | `organization` table | `OrganizationDirectory`, `OrganizationSummary` |
| `settings` | `shop_setting` table; `shopSettings` | — |

- **Schema:** `contracts/graphql/*.graphqls` is packaged into `classpath:graphql/` per implemented phase (see `<resources>` in `pom.xml`). `SchemaMappingIT` fails if a packaged field has no resolver.
- **Errors:** every GraphQL error has `extensions.code` from `contracts/errors.md`. Throw `ApplicationException(ErrorCode, safeMessage)` for expected failures; anything else becomes `INTERNAL` with a logged `correlationId`.
- **Migrations:** `src/main/resources/db/migration/V<NNNN>__<module_table>.sql`; data-model docs in `contracts/data-model/` change in the same commit.
- **Security:** only health probes and `/graphql` are reachable; each operation authorizes itself (decision D25).
