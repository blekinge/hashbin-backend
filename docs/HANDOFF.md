# Handoff — hashbin-quarkus

**Purpose of this file:** continuity record for a new session (e.g. after a
machine reboot) that no longer has the original work session's context. The
human-facing docs are in [`README.md`](../README.md); this captures *why* things
are the way they are and *what is left*.

## What this project is

A **Java / Quarkus port** of the **hashbin.net** trusted file-timestamping
backend, which originally shipped as a Supabase Edge Function (Deno + Hono) at
`/home/asga/git/github/blekinge/hashbin-time-vault`.

- Goal: reimplement the backend in a stack the owner maintains (Java/Quarkus),
  **wire-compatible** with the existing React frontend (API pages) and the
  existing Python CLI, and **remove the Supabase runtime dependency**.
- The original repo is **untouched** — this is a clean sibling project at
  `/home/asga/git/github/blekinge/hashbin-quarkus` (its own git repo).

## Stack / versions

- Quarkus **3.20.1** (LTS), **Java 21**, Maven.
- `quarkus-rest` + Jackson, `quarkus-hibernate-orm-panache`, `quarkus-jdbc-postgresql`,
  `quarkus-flyway`, `quarkus-smallrye-openapi` + Swagger UI, `quarkus-hibernate-validator`,
  `quarkus-elytron-security-common` (BCrypt), `com.nimbusds:nimbus-jose-jwt` (JWT, BOM-managed 9.48).

## Status: Phases 1–7 complete (backend is fully working, Supabase-free)

| Commit | Phase | What |
|---|---|---|
| `2612c1a` | 1 | Scaffold, config, Flyway V1 schema, `Timestamp` entity, `/api/health` |
| `fc44778` | 2 | `POST /api/stamp` + HMAC signing + per-IP rate limiting + validation/error parity |
| `30f7b72` | 3 | `GET /api/verify`, `GET /api/my-timestamps`, JWT auth (was Supabase round-trip) |
| `f99c929` | 4 | Serve auto-generated OpenAPI at `/api/openapi.json` (for the Docs page) |
| `a1f2d3f` | 5 | CLI compatibility verified end-to-end (`scripts/cli-compat-check.sh`) |
| `48d3ee7` | 6 | **Native email+password auth, own HS256 JWTs — off Supabase** |
| `7efdf05` | 7 | `GET /api/explore` — public projection, newest-first, `limit`/`offset` paging (Explore page off Supabase) |

Tests: **50/50 green** (`mvn test`).

## Key decisions & gotchas (do not silently "fix" these)

- **Build with JDK 21, not 25.** Default machine JDK is 25; Quarkus 3.20's Byte
  Buddy rejects Java 25 class files at Hibernate build time. Always:
  `export JAVA_HOME=~/.sdkman/candidates/java/21.0.9-tem`.
- **Dev Services disabled; Postgres is an explicit container on `localhost:5433`**
  (`docker compose up -d`). Reason: Testcontainers couldn't auto-detect Docker
  Engine 29 on the original machine. A reboot *might* fix Testcontainers, but the
  explicit container is deterministic — keep it. (`quarkus.devservices.enabled=false`
  in `application.properties`.)
- **JAX-RS base is `quarkus.rest.path=/api`**, *not* `quarkus.http.root-path`.
  `root-path` is not applied under `@QuarkusTest`; `rest.path` is, and it keeps
  `/q/openapi` & `/q/swagger-ui` at `/q`.
- **created_at** is generated once per stamp as ISO-8601 UTC millis `...Z`
  (`DateTimeFormatter` pattern in `TimestampView`), reused verbatim for the HMAC
  message, the DB write, and the response — so signatures are self-consistent and
  match the original Deno output byte-for-byte.
- **HMAC** = `HMAC-SHA256(secret, "{hash_sha256}:{created_at}")`, lowercase hex
  (`HmacSigner`). Proven against an RFC vector. **This is why old Supabase-stored
  rows still verify.**
- **Auth (Phase 6):** tokens are our own **HS256 JWTs**; MAC key = `SHA-256(secret)`
  (so any-length secret works, wrong secret just fails). `JwtService` implements
  the `AuthService` interface. `/api/stamp` attributes on a valid token, else
  anonymous; `/api/my-timestamps` + `/api/auth/me` require a valid token.
- **`/api/openapi.json`** reads Quarkus' internal `META-INF/quarkus-generated-openapi-doc.JSON`
  via `OpenApiConstants.classLoader` (TCCL doesn't see it in dev/test). Maintenance
  watch: a Quarkus upgrade could rename that resource — but `OpenApiSpecResourceTest`
  fails loudly if it does.
- **`user_id` is a bare UUID** (no FK) → stays decoupled from `app_users`/Supabase.
- **`GET /api/explore`** (Phase 7) reuses `TimestampPublicView` and orders by
  `createdAt DESC, id DESC`. The `id DESC` is a deliberate tiebreaker so offset
  paging stays stable when two stamps share a millisecond — not an accident; the
  original Supabase read ordered on `created_at` alone. `limit` defaults to 20
  (the frontend `PAGE_SIZE`) and is capped at **100** (the old direct-view read
  had no cap); `limit`/`offset` are validated manually to keep the `{"error":…}`
  400 shape consistent with `/verify`.

## Build / run / test (copy-paste)

```bash
cd /home/asga/git/github/blekinge/hashbin-quarkus
export JAVA_HOME=~/.sdkman/candidates/java/21.0.9-tem   # JDK 21 — REQUIRED
docker compose up -d                                     # Postgres on :5433
mvn quarkus:dev                                          # app on :8080
mvn test                                                 # needs the container up
```

Required env for production: `QUARKUS_DATASOURCE_JDBC_URL`, `QUARKUS_DATASOURCE_USERNAME`,
`QUARKUS_DATASOURCE_PASSWORD`, `HASHBIN_HMAC_SECRET`, `HASHBIN_JWT_SECRET`
(+ optional `HASHBIN_JWT_ISSUER`, `HASHBIN_JWT_TTL_MINUTES`). Dev defaults exist
in `application.properties` but are insecure — override them.

## What is LEFT to do

1. **Frontend rewiring (Auth + Explore)** — the React app still talks to Supabase
   directly for both pages; the backend now has endpoints to replace them.
   - `Auth.tsx` called the Supabase JS SDK (`signInWithPassword` / `signUp` /
     `signInWithOAuth("google")`). Point it at the new `POST /api/auth/register`,
     `POST /api/auth/login`, `GET /api/auth/me` (all return `{token,...}`). The
     browser must store/send that bearer on `/stamp` and `/my-timestamps`.
     (Google OAuth is not implemented — add an OAuth flow if still wanted.)
   - `Explore.tsx` read Supabase's `timestamps_public` view directly. Point it at
     `GET /api/explore?limit=&offset=` (returns the anonymized
     `TimestampPublicView`, newest-first; client-side hash-prefix filter already
     exists). Use `limit=PAGE_SIZE+1` to keep its `hasMore` detection working.
2. **Optional** — Java/Picocli CLI rewrite (Python CLI works fine and is verified);
   auth-email infra (the original `process-email-queue` + `email_infra` migration
   is Supabase/Lovable boilerplate, deliberately NOT ported — use `quarkus-mailer`
   if email confirmation/reset is needed).

## Hardening gaps (nice-to-know, not blocking)

- Rate limiter is in-memory/per-node (matches original); needs Redis if multi-replica.
- Auth endpoints are not rate-limited yet (`RateLimiter` could be reused with a
  tighter window).
- Registration race on identical email relies on the `uq_app_users_email` index;
  the explicit pre-check covers the normal path, the index covers the rare race
  (would surface as a 500 rather than 409 in a true simultaneous double-submit).
