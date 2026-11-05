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
- **Default DB is Quarkus Dev Services on Podman (rootless), not Docker** — as of
  2026-11-05. `application.properties` has **no dev/test JDBC URL**, so Dev Services
  activates and starts a `postgres:16-alpine` container (pinned via
  `quarkus.datasource.devservices.image-name`). Plain `mvn test` / `mvn quarkus:dev`
  work because the **machine-wide `~/.testcontainers.properties`** points Testcontainers
  at the rootless Podman socket (`docker.host=unix:///run/user/1000/podman/podman.sock`)
  and disables Ryuk (`ryuk.container.enabled=false`) — this fix applies to every
  Testcontainers project on this host, not just this repo. On a machine without that
  global config, run `systemctl --user start podman.socket` and export
  `DOCKER_HOST=unix://$XDG_RUNTIME_DIR/podman/podman.sock` and
  `TESTCONTAINERS_RYUK_DISABLED=true` before running.
  - *Why Podman:* Docker Engine 29 (this host, API 1.56) rejects the **v1.32** API
    that docker-java in Testcontainers 1.20.6 (Quarkus 3.20.1) negotiates —
    `400 client version 1.32 is too old. Minimum supported API version is 1.40` —
    so Dev Services can't start against Docker here (a reboot does NOT fix this, and
    `DOCKER_API_VERSION` (tried `1.40`/`1.44`) is ignored by 1.20.6's client
    selection). Podman's Docker-compat API ignores the version prefix and accepts
    v1.32, so Dev Services works. A Quarkus/Testcontainers upgrade (docker-java
    defaulting to ≥1.40) would also fix Docker, but isn't needed.
  - *Image store:* Podman does **not** share Docker's images. `postgres:16-alpine`
    must exist in Podman's store — `podman pull`, or offline
    `docker save postgres:16-alpine | podman load`. `testcontainers.reuse.enable=true`
    in `~/.testcontainers.properties` keeps one Postgres warm between runs (fast reuse;
    remove a stale one with `podman rm -f <id>`).
  - *External-DB fallback:* uncomment the `quarkus.datasource.jdbc.url/username/
    password` lines in `application.properties` (a set JDBC URL disables Dev Services)
    and point them at any reachable PostgreSQL. The old `docker-compose.yml` (and the
    `scripts/with-podman` wrapper) were removed on 2026-11-05 now that the engine is
    pinned machine-wide.
- **Serves the static web UI same-origin** via a file-activated Maven profile `web`:
  when `../hashbin-web/dist/index.html` exists it copies that build onto the classpath
  under `META-INF/resources`, so `mvn quarkus:dev` serves the UI at
  `http://localhost:8080/` and the API at `/api` together — no CORS, and `config.js`'s
  default `apiBase:"/api"` works as-is. The profile re-declares `src/main/resources`
  (a `<resources>` block replaces the inherited default and would otherwise drop
  `application.properties` + the Flyway migrations). Build the UI first
  (`cd ../hashbin-web && npm run build`); disable serving with `-P '!web'`. With the
  profile active, `mvn package` also bundles the UI into the jar (single-artifact
  deploy) — but the **recommended production model keeps them separate** (host the
  static `dist/` anywhere, point `config.js` at the API), which is why the UI is its
  own repo `../hashbin-web`.
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
mvn quarkus:dev                    # Dev Services on Podman (via ~/.testcontainers.properties); app on :8080
mvn test                           # same; 50 tests, ephemeral DB
```

Needs rootless Podman with `podman.socket` active; the machine-wide
`~/.testcontainers.properties` points Testcontainers at it and disables Ryuk. On a
machine without that config, export `DOCKER_HOST=unix://$XDG_RUNTIME_DIR/podman/podman.sock`
and `TESTCONTAINERS_RYUK_DISABLED=true` first. External-DB fallback: see the Dev
Services gotcha above.

Required env for production: `QUARKUS_DATASOURCE_JDBC_URL`, `QUARKUS_DATASOURCE_USERNAME`,
`QUARKUS_DATASOURCE_PASSWORD`, `HASHBIN_HMAC_SECRET`, `HASHBIN_JWT_SECRET`
(+ optional `HASHBIN_JWT_ISSUER`, `HASHBIN_JWT_TTL_MINUTES`). Dev/test uses an
ephemeral Dev Services DB (no DB creds to manage), but the dev HMAC/JWT secrets in
`application.properties` are insecure defaults — override them in production.

## What is LEFT to do

The web frontend is **done** as a separate, dependency-free static app in the sibling
repo `../hashbin-web` (vanilla TypeScript + `tsc`; no Lovable/Supabase). It was built
fresh rather than rewiring `hashbin-time-vault`, which is left untouched — that repo
remains a design/copy reference only. (See the "Serves the static web UI same-origin"
gotcha for the dev/prod wiring.)

1. **Optional** — Java/Picocli CLI rewrite (the Python CLI works and is verified via
   `scripts/cli-compat-check.sh`).
2. **Optional** — auth-email infra (the original `process-email-queue` + `email_infra`
   migration is Supabase/Lovable boilerplate, deliberately NOT ported — use
   `quarkus-mailer` if email confirmation/reset is wanted).
3. **Optional (frontend, in `../hashbin-web`)** — Google OAuth (no backend flow yet) and
   a CLI-usage page; both were left out to keep the new UI minimal.

## Hardening gaps (nice-to-know, not blocking)

- Rate limiter is in-memory/per-node (matches original); needs Redis if multi-replica.
- Auth endpoints are not rate-limited yet (`RateLimiter` could be reused with a
  tighter window).
- Registration race on identical email relies on the `uq_app_users_email` index;
  the explicit pre-check covers the normal path, the index covers the rare race
  (would surface as a 500 rather than 409 in a true simultaneous double-submit).
