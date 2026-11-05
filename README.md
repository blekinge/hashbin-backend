# hashbin-quarkus

Quarkus (Java) port of the **hashbin.net** trusted file-timestamping backend —
the original was a Supabase Edge Function written in Deno + Hono. This service is
**wire-compatible** with the existing React frontend and Python CLI: same routes
(`/api/stamp`, `/api/verify`, `/api/my-timestamps`, `/api/health`), same JSON
(snake_case), same HMAC signature scheme.

> Status: **Phase 3** — all four endpoints implemented: `/api/health`,
> `POST /api/stamp`, `GET /api/verify`, `GET /api/my-timestamps`, with HMAC
> signing, per-IP rate limiting, validation/error parity, public vs. private
> column projection, and Supabase JWT auth (token round-trip). OpenAPI/Swagger
> are auto-generated. Phase 4 adds the `/api/openapi.json` alias; Phase 5 is the
> end-to-end CLI compatibility check.

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
| Supabase URL | `SUPABASE_URL` | Base URL for the auth token round-trip |
| Supabase anon key | `SUPABASE_ANON_KEY` | Optional `apikey` header on the round-trip |

**Auth:** the optional bearer token on `/api/stamp` and the token required by
`/api/my-timestamps` are validated by exchanging them at
`{SUPABASE_URL}/auth/v1/user` — the same approach the original Deno function used.
It is deliberately *not* local JWKS verification, because Supabase signs tokens
with HS256 (a shared secret); the round-trip is algorithm-agnostic and honours
revocation. An invalid/absent token simply makes a stamp anonymous.

In dev/test the service uses the docker-compose Postgres (`localhost:5433`,
db/user/pass all `hashbin`). Quarkus **Dev Services is disabled** because this
project's Testcontainers build cannot auto-detect Docker Engine 29, so the
container is started explicitly.

## Layout

```
pom.xml
docker-compose.yml
src/main/java/net/hashbin/
  model/Timestamp.java              # JPA/Panache entity -> "timestamps" table
  service/HmacSigner.java           # HMAC-SHA256 signature (Deno-compatible)
  service/RateLimiter.java          # fixed-window per-IP limiter (10/min)
  service/TimestampService.java     # @Transactional persistence boundary
  service/AuthService.java          # bearer token -> user id (interface)
  service/SupabaseAuthService.java  # Supabase /auth/v1/user round-trip impl
  rest/HealthResource.java          # GET  /api/health
  rest/StampResource.java           # POST /api/stamp
  rest/VerifyResource.java          # GET  /api/verify?hash=&algorithm=
  rest/MyTimestampsResource.java     # GET  /api/my-timestamps (auth)
  rest/Errors.java                  # {"error": "..."} response builder
  rest/dto/TimestampView.java       # snake_case full projection
  rest/dto/TimestampPublicView.java # snake_case public projection (verify)
src/main/resources/
  application.properties
  db/migration/V1__create_timestamps.sql
src/test/java/net/hashbin/
  HealthResourceTest.java
  StampResourceTest.java
  VerifyResourceTest.java
  MyTimestampsResourceTest.java
  service/HmacSignerTest.java
  service/RateLimiterTest.java
  testsupport/FakeAuthService.java
```
