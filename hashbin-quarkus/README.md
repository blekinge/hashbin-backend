# hashbin-quarkus

Quarkus (Java) port of the **hashbin.net** trusted file-timestamping backend —
the original was a Supabase Edge Function written in Deno + Hono. This service is
**wire-compatible** with the existing Python CLI and the API-driven frontend
pages: same routes (`/api/stamp`, `/api/verify`, `/api/my-timestamps`,
`/api/health`), same JSON (snake_case), same HMAC signature scheme.

> **This is the service module of a Maven multi-module repo.** Everything
> Quarkus lives here. The sibling `hashbin-api` module is a Java client whose models
> are generated from this service's OpenAPI document (see below); the shared parent
> pom aggregates both. See the [root README](../README.md) for the reactor and how to
> use `hashbin-api` as a dependency. Commands below are shown for the module
> directory; from the
> repo root, add `-pl hashbin-quarkus` (e.g. `mvn -pl hashbin-quarkus quarkus:dev`).

> Status: **Complete (Phases 1–7)** — a self-hosted Quarkus port of the hashbin.net
> backend, **with no remaining Supabase dependency**. All endpoints are served
> under `/api` with HMAC signing, per-IP rate limiting, validation/error parity,
> public vs. private column projection, **native email + password auth that
> issues its own HS256 JWTs** (Phase 6), and a public `/api/explore` feed that backs
> the Explore page (Phase 7). The auto-generated OpenAPI document is
> exposed at `/q/openapi` and as an `/api/openapi.json` alias. The real Python CLI
> from the Lovable repo is verified against this service by
> `scripts/cli-compat-check.sh`.
>
> The React **Auth** page that called Supabase's client SDK directly is not ported;
> auth is replaced by the backend's `POST /api/auth/register`, `POST /api/auth/login`,
> `GET /api/auth/me` endpoints (consumed by the new `hashbin-web` frontend — see
> [Ecosystem](#ecosystem)).

> **Contributors:** build/runtime decisions and the "do not silently 'fix' these"
> gotchas are recorded in [Decisions & gotchas](#decisions--gotchas).

## Ecosystem

This service is one module of this repo; the sibling **`hashbin-api`** module is a
Java client whose models are generated from this service's OpenAPI document (so they
cannot drift). The repo itself lives alongside two further sibling repos (separate
repos, not a monorepo):

- **hashbin-quarkus** *(this module)* — the backend (Quarkus 3.40.1 / JDK 21). Dev/test
  DB via Quarkus Dev Services (throwaway Postgres through Testcontainers). A
  file-activated `web` Maven profile serves `../../hashbin-web/dist` same-origin. The
  native container-image build is wired (Docker extension + Mandrel container build).
- **hashbin-api** *(sibling module)* — Java client for the `/api` contract: models + a
  MicroProfile REST Client generated from this service's spec. No Quarkus dependency.
- **hashbin-web** — dependency-free static frontend (vanilla TypeScript + `tsc`; no
  Lovable/Supabase). `dist/` is git-ignored build output (run `npm run build`);
  `./build.sh` self-bootstraps the pinned Node; CI builds its nginx image from source.
- **hashbin-deploy** — Kubernetes (Kustomize) manifests for api + web + database
  (CloudNativePG, nginx web pod, single-origin `/api` routing, native api image).

The original `hashbin-time-vault` repo is **untouched** — a design/copy reference only.

## Requirements

- **Java 21** — required; Quarkus 3.20's bundled Byte Buddy rejects Java 25 class files.
- **Maven** 3.9+
- **A container engine** (Docker, or rootless **Podman**) reachable by Testcontainers — Dev
  Services starts the PostgreSQL it needs on demand. With Docker working out of the box,
  `mvn test` / `mvn quarkus:dev` just run. For rootless Podman, start the socket
  (`systemctl --user start podman.socket`) and export
  `DOCKER_HOST=unix://$XDG_RUNTIME_DIR/podman/podman.sock` and `TESTCONTAINERS_RYUK_DISABLED=true`
  (see [Decisions & gotchas](#decisions--gotchas) for why Podman is sometimes needed).

## Run

Dev Services starts a throwaway PostgreSQL on demand, so there is no manual database step.

```bash
export JAVA_HOME=<a JDK 21>     # JDK 21 required
mvn quarkus:dev
```

> On rootless Podman, first start `podman.socket` and export
> `DOCKER_HOST=unix://$XDG_RUNTIME_DIR/podman/podman.sock` and `TESTCONTAINERS_RYUK_DISABLED=true`.

**Web UI (same-origin):** if the sibling `../../hashbin-web` has been built (`npm run
build`), a file-activated Maven profile copies its `dist/` onto the classpath, so this
server also serves the UI at <http://localhost:8080/>. No CORS, and `config.js`'s default
`apiBase:"/api"` works as-is. Disable with `-P '!web'`.

Endpoints:

- Health:          <http://localhost:8080/api/health>
- Stamp:           `POST http://localhost:8080/api/stamp`
- Verify:          `GET  http://localhost:8080/api/verify?hash=&algorithm=`
- Explore:         `GET  http://localhost:8080/api/explore?limit=&offset=` (public, newest-first)
- My timestamps:   `GET  http://localhost:8080/api/my-timestamps` (bearer token)
- Register:        `POST http://localhost:8080/api/auth/register` (`{email,password}`)
- Login:           `POST http://localhost:8080/api/auth/login` (`{email,password}`)
- Me:              `GET  http://localhost:8080/api/auth/me` (bearer token)
- OpenAPI (alias): <http://localhost:8080/api/openapi.json>  ← used by the frontend Docs page
- OpenAPI (native): <http://localhost:8080/q/openapi>  (YAML/JSON, content-negotiated)
- Swagger UI:      <http://localhost:8080/q/swagger-ui>
- Dev UI:          <http://localhost:8080/q/dev-ui>

## Test

```bash
mvn test     # Dev Services starts an ephemeral Postgres via Testcontainers (50 tests)
```

> `MyTimestampsResourceTest.tamperedTokenIsUnauthorized` is intentionally robust: it
> replaces the **whole** JWT signature segment to force an invalid token. (Flipping a
> single base64url character — a common shortcut — is unreliable: a trailing character
> can encode fewer than 8 significant bits and decode to the identical bytes, leaving a
> valid token; that made the original test flaky.)

## OpenAPI artifact

`quarkus.smallrye-openapi.store-schema-directory` makes the `package` build write the
generated document to `target/generated-openapi/{openapi.yaml,openapi.json}`. A
classifier'd `maven-jar-plugin` execution then bundles those into a **secondary
artifact**:

```
hashbin-quarkus-<version>-openapi.jar     # contains openapi.yaml + openapi.json at the root
```

It is attached to the build and **consumed in-reactor by the sibling
[`hashbin-api`](../hashbin-api/README.md) module**, which unpacks it and generates its
client models **and MicroProfile REST Client interfaces** from it (so the client can
never drift from the service). Because its only consumer is that module in this same
reactor, the artifact is **not published to any Maven registry** — it is internal build
plumbing (the generated client also bundles the yaml/json at `META-INF/openapi/`). The
`hashbin-api` jar (and the service image) are what get published for external use.

```
net.hashbin:hashbin-quarkus:jar:openapi   # unpacked by hashbin-api's maven-dependency-plugin
```

## Container image

The build can produce the native container image that [`hashbin-deploy`](../../hashbin-deploy)
pulls (`ghcr.io/blekinge/hashbin-quarkus`). `quarkus-container-image-docker` is wired into
the pom; the image repo path lives in `application.properties`, the tag is set per release.
The native binary is compiled **inside a Mandrel builder container**
(`quarkus.native.container-build=true` in the `native` profile), so **no local GraalVM is
needed** — only a container engine.

**Native image (what deploy uses), via Docker:**

```bash
mvn -P '!web' -Dnative -DskipTests \
    -Dquarkus.container-image.build=true -Dquarkus.container-image.push=true \
    -Dquarkus.container-image.tag=1.0.0 package
# → ghcr.io/blekinge/hashbin-quarkus:1.0.0
```

On this host the engine is **rootless Podman** — add these flags (and export
`DOCKER_HOST=unix://$XDG_RUNTIME_DIR/podman/podman.sock`):

```bash
    -Dquarkus.docker.executable-name=podman -Dquarkus.native.container-runtime=podman
```

`-P '!web'` builds the API without the sibling frontend baked in (the deploy serves `web`
as a separate nginx pod). Drop it to bundle the UI into the same image.

**JVM image (quick, GraalVM-free smoke build):** same command without `-Dnative`, using
`src/main/docker/Dockerfile.jvm`. The `Dockerfile.native`/`Dockerfile.jvm` templates run as
non-root UID 185 on port 8080, matching the Deployment's `securityContext`/`containerPort`.

> This `mvn` command is the manual / local build (the `ghcr.io/blekinge/...` path). CI builds the
> same image a different way — a multi-stage `docker build` pushed to the org **Harbor** —
> see "CI (Jenkins)" below. A separate `web` image (nginx) is built from `../../hashbin-web` (via `Dockerfile.ci`).

## CI (Jenkins)

CI is a **self-contained pipeline** at [`ci/Jenkinsfile`](ci/Jenkinsfile) — no shared-library
calls. It runs in a Kubernetes pod with **three** containers:

- **`jdk`** (`eclipse-temurin:21-jdk-ubi9-minimal`) — builds and tests via `./mvnw`.
- **`postgres`** (`postgres:16-alpine`) — the throwaway test DB, gated by a `pg_isready`
  readiness probe so the build never races startup.
- **`docker`** (`docker:dind-rootless`, privileged) — a Docker daemon, used **only** by the
  image stage.

Two stages:

1. **Build and test** — `./mvnw -PallTests clean install`. The suite runs against the
   `postgres` **sidecar**, **not** Testcontainers: the datasource points at it via
   `QUARKUS_DATASOURCE_JDBC_URL/USERNAME/PASSWORD` with
   `QUARKUS_DATASOURCE_DEVSERVICES_ENABLED=false`. This deliberately sidesteps the
   docker-java API-version mismatch that breaks Dev Services against a modern DinD daemon
   (see [Decisions & gotchas](#decisions--gotchas)). Flyway migrates the empty schema at
   app start; all 50 tests run against the sidecar DB.
2. **Build and push image** — a multi-stage `docker build` **from source**, pushed to the org
   **Harbor** as
   `harbor.tools.nyx.kb.dk/blekinge/hashbin-quarkus:<version>-<branch>-sha.<sha>-build.<n>`
   (the `blekinge` library segment is pinned — the Bitbucket env vars are null on the GitHub
   job), using the `jenkins-harbor` credential. The image kind is chosen by the `BUILD_NATIVE`
   parameter: **default `false` → JVM image** from `Dockerfile.jvm-multi-stage` (Maven fast-jar
   build on a JRE base, fast), keeping CI quick; flip to `true` for
   `Dockerfile.native-multi-stage` (native binary compiled inside the Mandrel/JDK‑21 builder
   stage — the slow, multi‑minute, long‑term target). **On `main`/`master`** the same image is
   additionally tagged and pushed as **`latest`**.

**Why it isn't the shared `DockerfileBuild` / `DinDBuild` libraries:** `DockerfileBuild` (what the
sibling `xipax` uses) runs the suite *inside* a BuildKit image build, where there is no Docker
daemon for Testcontainers, so this project's DB-backed tests can't run; `DinDBuild` runs the tests
with a DinD sidecar but only publishes the **jar to Nexus**, not a container image — and a modern
`docker:dind-rootless` daemon rejects the older API v1.32 that Testcontainers' docker-java
negotiates, so it can't run *this*
suite's Dev Services tests either. Neither alone does both jobs, so the pipeline here avoids
Testcontainers for tests and does **test + image push in one run**. It reuses the same agent model
as `DinDBuild` and the same Harbor naming + `jenkins-harbor` credential as `DockerfileBuild`.

The pipeline requires the committed Maven wrapper (`./mvnw`, `.mvn/wrapper/`), the no-op
`allTests` Maven profile, and a `.dockerignore` (trims the build context).

**Registry split (deliberate):** CI pushes the **org Harbor** image. The
**`ghcr.io/blekinge/hashbin-quarkus`** image that `hashbin-deploy` references is the *OSS / local*
path, built by hand with the `mvn … package` command in [Container image](#container-image).
Two registries, the same native image.

## Configuration

| Property | Env var (prod) | Purpose |
|---|---|---|
| Datasource URL | `QUARKUS_DATASOURCE_JDBC_URL` | JDBC connection |
| Datasource user | `QUARKUS_DATASOURCE_USERNAME` | DB user |
| Datasource password | `QUARKUS_DATASOURCE_PASSWORD` | DB password |
| HMAC secret | `HASHBIN_HMAC_SECRET` | Signs `{hash_sha256}:{created_at}` |
| JWT secret | `HASHBIN_JWT_SECRET` | HS256 signing key for issued tokens (MAC key = SHA-256(secret)) |
| JWT issuer | `HASHBIN_JWT_ISSUER` | `iss` claim, default `hashbin` |
| Token TTL | `HASHBIN_JWT_TTL_MINUTES` | Access-token lifetime, default 1440 (24h) |

Dev/test uses an **ephemeral Dev Services DB** (no DB creds to manage), but the dev
HMAC/JWT secrets in `application.properties` are insecure defaults — override them in
production. As a fallback you can point at any external PostgreSQL by setting the JDBC URL
(a set URL disables Dev Services; see the comments in `application.properties`).

**Auth (Phase 6, off Supabase):** accounts live in the local `app_users` table with
BCrypt password hashes (`quarkus-elytron-security-common`). `POST /api/auth/register`
and `POST /api/auth/login` return a self-issued **HS256 JWT** (`iss=hashbin`,
`sub=<user uuid>`, `email`, `iat`, `exp`). `JwtService` verifies those tokens and
backs the `AuthService` interface used elsewhere: a valid bearer on `/api/stamp`
attributes the record to that user; an invalid/absent token makes the stamp
anonymous; `/api/my-timestamps` and `/api/auth/me` require a valid token
(`401` otherwise). No Supabase is contacted at runtime.

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
mvn quarkus:dev -Dquarkus.http.port=8093        # Dev Services on Podman; in another shell
HASHBIN_API_BASE=http://localhost:8093/api ./scripts/cli-compat-check.sh
```

It patches a throwaway copy of the CLI's `API_BASE` and never edits the original.
`HASHBIN_CLI` and `HASHBIN_HMAC_SECRET` override the CLI path and secret.

## Decisions & gotchas

Design decisions and non-obvious constraints, kept here so a fresh session doesn't
"fix" something on purpose. (Human-facing usage docs live above; full deploy wiring
lives in `../../hashbin-deploy`.)

- **Build with JDK 21, not 25.** Quarkus 3.20's Byte Buddy rejects Java 25 class files at
  Hibernate build time. Point `JAVA_HOME` at any JDK 21.
- **Dev/test DB is Quarkus Dev Services** — `application.properties` has **no dev/test JDBC
  URL**, so Dev Services starts a throwaway `postgres:16-alpine` (image pinned via
  `quarkus.datasource.devservices.image-name`) through Testcontainers.
  - *Why Podman is sometimes needed:* some Docker Engine releases reject the older Docker API
    version (v1.32) that legacy docker-java/Testcontainers clients negotiate — modern daemons want ≥ 1.40 and `DOCKER_API_VERSION` is ignored by that client.
    Podman's Docker-compat API accepts it; a Quarkus/Testcontainers upgrade also fixes it on
    Docker. Podman setup (socket + `DOCKER_HOST` + `TESTCONTAINERS_RYUK_DISABLED=true`, and
    making sure `postgres:16-alpine` is in Podman's image store) is in [Requirements](#requirements).
  - *External-DB fallback:* set the `quarkus.datasource.jdbc.url/username/password` lines (a set
    JDBC URL disables Dev Services).
- **Same-origin web UI via a file-activated Maven profile `web`:** when
  `../../hashbin-web/dist/index.html` exists, the profile copies that build onto the classpath under
  `META-INF/resources`, so `mvn quarkus:dev` serves UI + API on one origin (no CORS). The profile
  **re-declares** `src/main/resources` (a `<resources>` block replaces the inherited default and
  would otherwise drop `application.properties` + Flyway migrations). Build the UI first; disable
  serving with `-P '!web'`. Production keeps UI and API separate (host `dist/` anywhere, point
  `config.js` at the API) — which is why the UI is its own repo.
- **JAX-RS base is `quarkus.rest.path=/api`**, *not* `quarkus.http.root-path` — `root-path` is
  not applied under `@QuarkusTest`; `rest.path` is, and it keeps `/q/openapi` & `/q/swagger-ui`
  under `/q`.
- **`created_at` is generated once per stamp** as ISO-8601 UTC millis `...Z` (pattern in
  `TimestampView`) and reused verbatim for the HMAC message, the DB write, and the response — so
  signatures are self-consistent and match the original Deno output byte-for-byte.
- **HMAC** = `HMAC-SHA256(secret, "{hash_sha256}:{created_at}")`, lowercase hex (`HmacSigner`),
  proven against an RFC vector. **This is why old Supabase-stored rows still verify.**
- **JWT MAC key = `SHA-256(secret)`** (so any-length secret works; wrong secret just fails).
- **`/api/openapi.json`** reads Quarkus' internal `META-INF/quarkus-generated-openapi-doc.JSON`
  via `OpenApiConstants.classLoader` (the TCCL doesn't see it in dev/test). A Quarkus upgrade could
  rename that resource — but `OpenApiSpecResourceTest` fails loudly if it does.
- **`user_id` is a bare UUID** (no FK) → stays decoupled from `app_users`/Supabase.
- **`GET /api/explore`** reuses `TimestampPublicView` and orders by `createdAt DESC, id DESC`. The
  `id DESC` is a deliberate tiebreaker so offset paging stays stable when two stamps share a
  millisecond (the original Supabase read ordered on `created_at` alone). `limit` defaults to 20
  (the frontend `PAGE_SIZE`) and is capped at **100** (the old direct view read had no cap);
  `limit`/`offset` are validated manually to keep the `{"error":…}` 400 shape consistent with
  `/verify`.
- **Native serialization: response DTOs are `@RegisterForReflection`.** Resources return
  `Response.ok(<dto>)`, so the DTO type isn't in a method signature and the native build does NOT
  auto-register it for Jackson. Without the annotation the **JVM** suite is green (runtime
  reflection) but the **native** image serializes an empty `200 {}` (e.g. `/api/stamp` writes and
  signs the row, returns nothing). Applies to `TimestampView`, `TimestampPublicView`,
  `AuthResponse`, `MeView`; JDK-type bodies (`Map.of(...)`) are unaffected. **The 50 tests are
  JVM-only `@QuarkusTest`, so they cannot catch this class of regression** — verify by running the
  built native image; a permanent fix would be a `@QuarkusIntegrationTest` against it.

## Open work & hardening

The frontend was built fresh in `../../hashbin-web` (vanilla TS, no Lovable/Supabase) rather than
rewiring `hashbin-time-vault`. Remaining items, none blocking:

- **Optional** — Java/Picocli CLI rewrite (the Python CLI works and is verified via
  `scripts/cli-compat-check.sh`).
- **Optional** — auth-email infra (the original `process-email-queue` + `email_infra` migration is
  Supabase/Lovable boilerplate, deliberately **not** ported — use `quarkus-mailer` if email
  confirmation/reset is wanted).
- **Optional (frontend, in `../../hashbin-web`)** — Google OAuth (no backend flow yet) and a
  CLI-usage page; both left out to keep the new UI minimal.
- **Health probes** — add `quarkus-smallrye-health` for real liveness/readiness (the current k8s
  probes hit the static `/api/health`).
- **Hardening** — the rate limiter is in-memory/per-node (matches the original; needs Redis if
  multi-replica); auth endpoints are not rate-limited yet (`RateLimiter` could be reused with a
  tighter window); registration on an identical email relies on the `uq_app_users_email` unique
  index — the pre-check covers the normal path, the index covers a true simultaneous double-submit
  (which would surface as a 500 rather than 409).

*Deploy-side follow-ups (pin the production image by `digest:`, CloudNativePG backups → object
storage, metrics-driven autoscaling, edge rate-limiting) are tracked in `../../hashbin-deploy`.*

## Layout

```
pom.xml                               # parent aggregator (hashbin-parent, packaging=pom): modules + shared build config
mvnw / .mvn/wrapper/                  # Maven wrapper (the pipeline invokes ./mvnw at the repo root)
ci/Jenkinsfile                        # self-contained Jenkins CI: test on a postgres sidecar + build/push image to Harbor
scripts/cli-compat-check.sh           # drives the real (Python) CLI against this service

hashbin-quarkus/                      # -- the service module (all Quarkus lives here) --
  pom.xml                             # Quarkus BOM + plugin, native + web profiles; artifactId stays hashbin-quarkus
  src/main/java/net/hashbin/
    model/Timestamp.java              # JPA/Panache entity -> "timestamps" table
    model/AppUser.java                # JPA/Panache entity -> "app_users" table (auth)
    service/HmacSigner.java           # HMAC-SHA256 signature (Deno-compatible)
    service/RateLimiter.java          # fixed-window per-IP limiter (10/min)
    service/TimestampService.java     # @Transactional persistence boundary
    service/AuthService.java          # bearer token -> user id (interface)
    service/JwtService.java           # issues + verifies HS256 JWTs (impl of AuthService)
    service/UserService.java          # register / authenticate (BCrypt)
    rest/HealthResource.java          # GET  /api/health
    rest/StampResource.java           # POST /api/stamp
    rest/VerifyResource.java          # GET  /api/verify?hash=&algorithm=
    rest/MyTimestampsResource.java    # GET  /api/my-timestamps (auth)
    rest/ExploreResource.java         # GET  /api/explore (public)
    rest/AuthResource.java            # POST /api/auth/{register,login}, GET /api/auth/me
    rest/OpenApiSpecResource.java     # GET  /api/openapi.json (spec alias)
    rest/dto/                          # snake_case projections + request/response payloads
  src/main/resources/
    application.properties
    db/migration/V1__create_timestamps.sql
    db/migration/V2__create_app_users.sql
  src/main/docker/
    Dockerfile.jvm-multi-stage        # JVM image BUILT FROM SOURCE -- what CI docker-builds and pushes
    Dockerfile.native-multi-stage     # native image BUILT FROM SOURCE (BUILD_NATIVE=true)
    Dockerfile.jvm / Dockerfile.native # hand-built (mvn -Dquarkus.container-image.build), module-dir context
  src/test/java/net/hashbin/          # @QuarkusTest suite (Dev Services Postgres)

hashbin-api/                          # -- the client module (models + client GENERATED from the spec) --
  pom.xml                             # openapi-generator (java/microprofile) + Jackson; bundles META-INF/openapi
  src/main/java/net/hashbin/api/
    ApiException.java                 # hand-written: the error type the generated ops declare/throw
    HashbinErrorMapper.java           # hand-written: non-2xx -> ApiException (status + {"error":...})
    auth/BearerClientHeadersFactory.java  # hand-written: adds Authorization: Bearer for the generated client
    # model/* AND net.hashbin.api.{Timestamps,Auth,Health,Docs}Api are GENERATED at build time into
    # target/generated-sources/openapi (not committed): Jackson POJOs + @RegisterRestClient interfaces.
  src/test/java/net/hashbin/api/      # plain-JUnit: generated-API/spec/bearer/error-mapper tests
```

## License

Released under the **Apache License, Version 2.0** — see [LICENSE](LICENSE).
Copyright 2025 Hashbin Contributors.
