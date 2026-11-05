# Handoff — hashbin-quarkus

**Purpose of this file:** continuity record for a new session (e.g. after a
machine reboot) that no longer has the original work session's context. The
human-facing docs are in [`README.md`](../README.md); this captures *why* things
are the way they are and *what is left*.

## What this project is

A **Java / Quarkus port** of the **hashbin.net** trusted file-timestamping
backend, which originally shipped as a Supabase Edge Function (Deno + Hono) in a
sibling repo (`hashbin-time-vault`).

- Goal: reimplement the backend in a stack the owner maintains (Java/Quarkus),
  **wire-compatible** with the existing React frontend (API pages) and the
  existing Python CLI, and **remove the Supabase runtime dependency**.
- The original repo is **untouched** — this is a clean, independent sibling project
  (`hashbin-quarkus`, its own git repo).

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

- **Build with JDK 21, not 25.** Quarkus 3.20's Byte Buddy rejects Java 25 class
  files at Hibernate build time. Point `JAVA_HOME` at any JDK 21 (e.g. via sdkman
  or your system package manager).
- **Dev/test database is Quarkus Dev Services** — `application.properties` has **no dev/test
  JDBC URL**, so Dev Services starts a throwaway `postgres:16-alpine` container (image pinned via
  `quarkus.datasource.devservices.image-name`) through **Testcontainers**. You need a container
  engine reachable by Testcontainers. Where Docker works out of the box, plain `mvn test` /
  `mvn quarkus:dev` just run.
  - *Rootless Podman alternative:* if you need Podman instead of Docker, start its socket
    (`systemctl --user start podman.socket`) and point Testcontainers at it — export
    `DOCKER_HOST=unix://$XDG_RUNTIME_DIR/podman/podman.sock` and `TESTCONTAINERS_RYUK_DISABLED=true`
    (or set `docker.host` / `ryuk.container.enabled=false` in a `~/.testcontainers.properties`).
    Some Docker Engine releases reject the older Docker API version that docker-java in
    Testcontainers 1.20.6 (bundled with Quarkus 3.20.1) negotiates; Podman's Docker-compat API
    accepts it. A Quarkus/Testcontainers upgrade (newer docker-java) also resolves it on Docker.
  - *Image store (Podman):* Podman does **not** share Docker's images — ensure `postgres:16-alpine`
    is present in Podman's store (`podman pull`, or `docker save postgres:16-alpine | podman load`).
  - *External-DB fallback:* uncomment the `quarkus.datasource.jdbc.url/username/password` lines in
    `application.properties` (a set JDBC URL disables Dev Services) and point them at any reachable
    PostgreSQL.
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
- **Native serialization: the response DTOs are `@RegisterForReflection`.** Every resource
  returns `Response.ok(<dto>)`, so the DTO type is not in a method signature and Quarkus' native
  build does NOT auto-register it for Jackson. Without the annotation the **JVM** suite is green
  (runtime reflection) but the **native** image serializes an empty `200 {}` (e.g. `/api/stamp`
  writes the row, signs it, returns nothing). Applies to `TimestampView`, `TimestampPublicView`,
  `AuthResponse`, `MeView`. JDK-type bodies (`HealthResource`, `Errors` → `Map.of(...)`) are
  unaffected. **The 50 tests are JVM-only `@QuarkusTest`, so they cannot catch this class of
  regression** — verified instead by running the built native image (see "Container image");
  a permanent fix would be a `@QuarkusIntegrationTest` against the native image.

## Build / run / test (copy-paste)

```bash
export JAVA_HOME=<a JDK 21>        # REQUIRED — Quarkus 3.20 fails to build on JDK 25
mvn quarkus:dev                    # Dev Services starts an ephemeral Postgres; app on :8080
mvn test                           # same; 50 tests, ephemeral DB
```

Needs a container engine reachable by Testcontainers (Docker, or rootless Podman with
`podman.socket` active — see the Dev Services gotcha above). External-DB fallback is described
there too.

Required env for production: `QUARKUS_DATASOURCE_JDBC_URL`, `QUARKUS_DATASOURCE_USERNAME`,
`QUARKUS_DATASOURCE_PASSWORD`, `HASHBIN_HMAC_SECRET`, `HASHBIN_JWT_SECRET`
(+ optional `HASHBIN_JWT_ISSUER`, `HASHBIN_JWT_TTL_MINUTES`). Dev/test uses an
ephemeral Dev Services DB (no DB creds to manage), but the dev HMAC/JWT secrets in
`application.properties` are insecure defaults — override them in production.

## Container image (wired 2026-11-05)

- The build produces the native image `hashbin-deploy` pulls. `quarkus-container-image-docker`
  (a build-time-only extension, so `mvn test` / `mvn package` are unchanged) is in the pom;
  **image path** (`ghcr.io/blekinge/hashbin-quarkus`) lives in `application.properties`, the
  **tag** is passed per release.
- The `native` Maven profile sets `quarkus.native.container-build=true`: the native binary is
  compiled inside a **Mandrel builder container**, so no local GraalVM/Mandrel is required
  (this host has none). Needs only a container engine.
- `src/main/docker/Dockerfile.native` and `Dockerfile.jvm` are required by the extension (it
  errors "Unable to find root of Dockerfile files" without them). Both run **non-root (UID
  185)** on **:8080** to match the Deployment's `securityContext`/`containerPort`.
- **No CI on purpose** (decision): native builds are heavy (minutes + a few GB). The command
  is in the README "Container image" section. The web image is built from `../hashbin-web`.
- On **Podman** (this host) add `-Dquarkus.docker.executable-name=podman
  -Dquarkus.native.container-runtime=podman` and export the Podman `DOCKER_HOST`. (The
  Docker-vs-Podman API problem above affects Testcontainers/Dev Services, not this image
  build, which shells out to the engine CLI — but the host is pinned to Podman regardless.)

## CI (Jenkins — self-contained, no shared library)

- Pipeline at **`ci/Jenkinsfile`** (not repo root — the org runs two controllers that find
  pipelines by path; `ci/Jenkinsfile` is the current controller's Script Path). It writes the
  whole pipeline inline rather than calling the `jenkins-shared` `Platform` lib.
- **Why inline, not `DockerfileBuild` (xipax) or `DinDBuild`:** neither library does both jobs this
  project needs. `DockerfileBuild` drives a 5-stage Dockerfile and runs `mvn verify` *inside* a
  BuildKit image build → **no Docker daemon for Testcontainers**, so the Dev Services tests can't
  start Postgres. `DinDBuild` runs the tests with a `docker:dind-rootless` sidecar but only
  publishes the **jar to Nexus**, never an image. So `ci/Jenkinsfile` does both in one pod.
- **Agent:** a Kubernetes pod with two containers — `jdk` (`eclipse-temurin:21-jdk-ubi9-minimal`,
  builds + tests via `./mvnw`, Testcontainers pointed at the sidecar through
  `DOCKER_HOST=tcp://localhost:2376`, Ryuk disabled) and `docker` (`docker:dind-rootless`,
  privileged; runs the `docker build`/`push`). Pod model mirrors `DinDBuild`; Harbor naming +
  the `jenkins-harbor` credential mirror `DockerfileBuild`.
- **Stages:** (1) `./mvnw -PallTests clean install` → JVM compile + the 50-test suite on a Dev
  Services DB. (2) `docker build` of **`src/main/docker/Dockerfile.native-multi-stage`** → the
  native binary compiles **inside the Mandrel/JDK‑21 builder stage** (`native-image` runs there, so
  `quarkus.native.container-build=false` — no nested container build, no GraalVM on the agent),
  then a minimal runtime image is pushed to `harbor.tools.nyx.kb.dk/<BITBUCKET_PROJECT_KEY>/
  <BITBUCKET_REPOSITORY>:<version>-<branch>-sha.<sha>-build.<n>`.
- **Prereqs the pipeline assumes** (all committed): Maven wrapper `./mvnw` + `.mvn/wrapper/`
  (`only-script`, Maven 3.9.16 — the pipeline invokes `./mvnw`, not `mvn`), a no-op **`allTests`**
  profile, and a **`.dockerignore`** (trims the build context; `target/` is kept so the
  single-stage hand-build Dockerfiles still work).
- **Registry split (deliberate):** CI pushes the **org Harbor** image (xipax-style). The
  **`ghcr.io/blekinge/hashbin-quarkus`** image that `hashbin-deploy` references is the *OSS /
  local* path, built by hand with `mvn -Dnative … package` (README "Container image"). Two
  registries, same native image.
- **Local parity checks** (rootless Podman stands in for the sidecar / `docker build`):
  `./mvnw -PallTests clean install` → 50 tests, BUILD SUCCESS; `buildah build --target final
  -f src/main/docker/Dockerfile.native-multi-stage` → native image builds.
- **Unverified in this environment:** the actual Jenkins/Kubernetes run (no Jenkins controller
  here), the `jenkins-harbor` credential, and `BITBUCKET_PROJECT_KEY`/`BITBUCKET_REPOSITORY`
  env vars — these exist only on the org controller. The pipeline mirrors the working shared-lib
  patterns but has not executed there end-to-end.

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
