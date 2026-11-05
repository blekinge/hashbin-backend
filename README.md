# hashbin-quarkus

Quarkus (Java) port of the **hashbin.net** trusted file-timestamping backend —
the original was a Supabase Edge Function written in Deno + Hono. This service is
**wire-compatible** with the existing React frontend and Python CLI: same routes
(`/api/stamp`, `/api/verify`, `/api/my-timestamps`, `/api/health`), same JSON
(snake_case), same HMAC signature scheme.

> Status: **Phase 1** — project scaffold, configuration, Flyway schema, `Timestamp`
> entity, and `/api/health`. The `stamp` / `verify` / `my-timestamps` endpoints,
> HMAC signing, rate limiting and JWT auth land in later phases.

## Requirements

- **Java 21** (Temurin). The machine default here is Java 25, which Quarkus 3.20's
  bundled Byte Buddy does not yet support — build with JDK 21.
- **Maven** 3.9+
- **Docker** (for the PostgreSQL container)

## Run

```bash
# 1. Start PostgreSQL (maps to localhost:5433)
docker compose up -d

# 2. Build / run on Java 21
export JAVA_HOME=~/.sdkman/candidates/java/21.0.9-tem   # or any JDK 21
mvn quarkus:dev
```

Endpoints:

- Health:        <http://localhost:8080/api/health>
- Swagger UI:    <http://localhost:8080/q/swagger-ui>
- OpenAPI spec:  <http://localhost:8080/q/openapi>
- Dev UI:        <http://localhost:8080/q/dev-ui>

## Test

```bash
docker compose up -d      # tests connect to the same Postgres container
mvn test
```

## Configuration

| Property | Env var (prod) | Purpose |
|---|---|---|
| Datasource URL | `QUARKUS_DATASOURCE_JDBC_URL` | JDBC connection |
| Datasource user | `QUARKUS_DATASOURCE_USERNAME` | DB user |
| Datasource password | `QUARKUS_DATASOURCE_PASSWORD` | DB password |
| HMAC secret | `HASHBIN_HMAC_SECRET` | Signs `{hash_sha256}:{created_at}` |

In dev/test the service uses the docker-compose Postgres (`localhost:5433`,
db/user/pass all `hashbin`). Quarkus **Dev Services is disabled** because this
project's Testcontainers build cannot auto-detect Docker Engine 29, so the
container is started explicitly.

## Layout

```
pom.xml
docker-compose.yml
src/main/java/net/hashbin/
  model/Timestamp.java        # JPA/Panache entity -> "timestamps" table
  rest/HealthResource.java    # GET /api/health
src/main/resources/
  application.properties
  db/migration/V1__create_timestamps.sql
src/test/java/net/hashbin/
  HealthResourceTest.java
```
