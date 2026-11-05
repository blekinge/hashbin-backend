# hashbin (backend)

Maven **multi-module** project for the **hashbin.net** trusted file-timestamping
backend. It is split so that the service framework and the client library are
independent build units:

| Module | What it is | Build |
|---|---|---|
| **`hashbin-quarkus`** | The service / Docker image. **All Quarkus lives here.** | Quarkus 3.40.1 / JDK 21 |
| **`hashbin-api`** | Java client: **models + a MicroProfile REST Client generated from the service's OpenAPI.** | JDK 21, openapi-generator + Jackson |

A parent POM (`hashbin-parent`, `packaging=pom`) aggregates the two and holds only
shared, Quarkus-free build config.

## Why split into these two modules

`hashbin-api`'s models and client interfaces are **regenerated on every build from the
OpenAPI document the `hashbin-quarkus` module emits** (in-reactor — the spec artifact
never leaves the repo). So the client can never drift from the service: a contract change
becomes a compile error downstream automatically. The `hashbin-api` **jar** is then
published so external JVM clients (e.g. the separate `hashbin-cli` repo) can depend on it
as a versioned Maven artifact:

```xml
<dependency>
  <groupId>net.hashbin</groupId>
  <artifactId>hashbin-api</artifactId>
  <version>1.0.0-SNAPSHOT</version>
</dependency>
```

```java
// The generated MicroProfile REST Client (base URL via quarkus.rest-client.hashbin.url):
@Inject @RestClient TimestampsApi timestamps;

// hash a file yourself (the client is contract-only, no file-IO concern), then stamp:
TimestampView ts = timestamps.apiStampPost(
        new StampRequest().hashSha256(sha256).fileSize(size).fileName("report.pdf"),
        null, null);

List<TimestampPublicView> history = timestamps.apiVerifyGet("auto", sha256);
```

The bearer token is supplied with `BearerClientHeadersFactory` (SmallRye filters
`Authorization` from the generated parameters); non-2xx responses throw the generated
`ApiException` via the generated `ApiExceptionMapper`. See [`hashbin-api`](hashbin-api/README.md).

## Build

```bash
export JAVA_HOME=<a JDK 21>     # Quarkus's bundled toolchain rejects JDK 25 class files

./mvnw clean install            # builds + tests both modules
./mvnw -pl hashbin-api test     # just the client library's tests (no container needed)
./mvnw -pl hashbin-quarkus test # the service suite (Dev Services spins up Postgres via Testcontainers)
```

## Run the service

From the repo root, address the module with `-pl` (or `cd hashbin-quarkus` first):

```bash
./mvnw -pl hashbin-quarkus quarkus:dev     # http://localhost:8080/api/health
```

The **`web`** profile, auto-activated when `../hashbin-web/dist/index.html` exists,
serves the frontend same-origin. The **`native`** profile builds the deploy image.
Full details, endpoints, config, CI, and gotchas: **[`hashbin-quarkus/README.md`](hashbin-quarkus/README.md)**.

## Container image

CI (`ci/Jenkinsfile`) builds **from source** inside a multi-stage recipe and pushes
to the org Harbor (`ghcr.io/blekinge/hashbin-backend` for manual builds). Those
recipes scope the Maven build to the service module (`-pl hashbin-quarkus -am`) and
read artifacts from `hashbin-quarkus/target/...`. See the service README.

## Ecosystem

- **hashbin-quarkus** *(this repo, `hashbin-quarkus` module)* — the backend.
- **hashbin-api** *(this repo, `hashbin-api` module)* — Java client (models generated from the spec).
- **hashbin-cli** — standalone Quarkus Picocli command line client. *(separate repo)*
- **hashbin-web** — dependency-free static frontend. *(separate repo)*
- **hashbin-deploy** — Kubernetes / Kustomize manifests. *(separate repo)*

## License

Apache-2.0 — see [LICENSE](LICENSE).
