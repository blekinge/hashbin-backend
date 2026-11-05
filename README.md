# hashbin-quarkus

Quarkus (Java) port of the **hashbin.net** trusted file-timestamping backend —
the original was a Supabase Edge Function written in Deno + Hono. This service is
**wire-compatible** with the existing React frontend and Python CLI: same routes
(`/api/stamp`, `/api/verify`, `/api/my-timestamps`, `/api/health`), same JSON
(snake_case), same HMAC signature scheme.

> Status: **Complete (Phases 1–5)** — a drop-in Quarkus port of the hashbin.net
> backend. All four endpoints are served under `/api` with HMAC signing, per-IP
> rate limiting, validation/error parity, public vs. private column projection,
> and Supabase JWT auth. The auto-generated OpenAPI document is exposed both at
> `/q/openapi` and as an `/api/openapi.json` alias for the existing frontend. The
> real Python CLI from the Lovable repo is verified against this service by
> `scripts/cli-compat-check.sh`.

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

- Health:          <http://localhost:8080/api/health>
- Stamp:           `POST http://localhost:8080/api/stamp`
- Verify:          `GET  http://localhost:8080/api/verify?hash=&algorithm=`
- My timestamps:   `GET  http://localhost:8080/api/my-timestamps` (bearer token)
- OpenAPI (alias): <http://localhost:8080/api/openapi.json>  ← used by the frontend Docs page
- OpenAPI (native): <http://localhost:8080/q/openapi>  (YAML/JSON, content-negotiated)
- Swagger UI:      <http://localhost:8080/q/swagger-ui>
- Dev UI:          <http://localhost:8080/q/dev-ui>

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

## CLI compatibility check

`scripts/cli-compat-check.sh` drives the **real** `hashbin` Python CLI (from the
sibling Lovable repo) against a running instance of this service and asserts the
port is a drop-in replacement:

1. `hashbin health` reports healthy.
2. `hashbin stamp <file>` returns a timestamp for all four digests.
3. `hashbin verify <file>` finds the record.
4. the stored `server_signature` recomputes as
   `HMAC-SHA256(secret, "{hash_sha256}:{created_at}")`.

```bash
docker compose up -d
mvn quarkus:dev -Dquarkus.http.port=8093        # in another shell
HASHBIN_API_BASE=http://localhost:8093/api ./scripts/cli-compat-check.sh
```

It patches a throwaway copy of the CLI's `API_BASE` and never edits the original.
`HASHBIN_CLI` and `HASHBIN_HMAC_SECRET` override the CLI path and secret.

## Layout

```
pom.xml
docker-compose.yml
scripts/cli-compat-check.sh         # Phase 5: drives the real CLI against this service
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
  rest/OpenApiSpecResource.java      # GET  /api/openapi.json (spec alias)
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
  OpenApiSpecResourceTest.java
  service/HmacSignerTest.java
  service/RateLimiterTest.java
  testsupport/FakeAuthService.java
```
