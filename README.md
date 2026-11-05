# hashbin (backend)

Maven **multi-module** project for the **hashbin.net** trusted file-timestamping
backend. It is split so that the service framework and the client library are
independent build units:

| Module | What it is | Build |
|---|---|---|
| **`hashbin-quarkus`** | The service / Docker image. **All Quarkus lives here.** | Quarkus 3.40.1 / JDK 21 |
| **`hashbin-api`** | A **plain-Java client** for the `/api` contract. No Quarkus. | JDK 21, Jackson only |

A parent POM (`hashbin-parent`, `packaging=pom`) aggregates the two and holds only
shared, Quarkus-free build config.

## Why split into these two modules

`hashbin-api` exists so **local JVM clients can depend on the API as a plain Maven
artifact** and tooling (Dependabot/Renovate) can bump that version to surface a
backend **contract change** as a *compile* error rather than a runtime surprise:

```xml
<dependency>
  <groupId>net.hashbin</groupId>
  <artifactId>hashbin-api</artifactId>
  <version>1.0.0-SNAPSHOT</version>
</dependency>
```

```java
HashbinClient client = new HashbinClient("https://hashbin.net/api");

// hash a file yourself (the client is contract-only, no file-IO concern), then stamp:
TimestampRecord ts = client.stamp(
        StampRequest.builder().sha256(sha256).fileSize(size).fileName("report.pdf").build(),
        token /* or null for anonymous */);

// independently confirm the record was signed by a legitimate server:
boolean authentic = Signature.verify(System.getenv("HASHBIN_HMAC_SECRET"), ts);

List<TimestampRecord> history = client.verify(sha256, "auto");
```

The client couples to the backend by its **wire contract** only (routes, snake_case
JSON, `{"error":...}` errors) plus the **HMAC verification** scheme — the one part
of the contract an OpenAPI document cannot express, which is why the client is
hand-written rather than code-generated from the spec. See
[`hashbin-api`](hashbin-api/README.md).

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
to the org Harbor (`ghcr.io/blekinge/hashbin-quarkus` for manual builds). Those
recipes scope the Maven build to the service module (`-pl hashbin-quarkus -am`) and
read artifacts from `hashbin-quarkus/target/...`. See the service README.

## Ecosystem

- **hashbin-quarkus** *(this repo, `hashbin-quarkus` module)* — the backend.
- **hashbin-api** *(this repo, `hashbin-api` module)* — the plain-Java client.
- **hashbin-cli** — standalone Quarkus Picocli command line client. *(separate repo)*
- **hashbin-web** — dependency-free static frontend. *(separate repo)*
- **hashbin-deploy** — Kubernetes / Kustomize manifests. *(separate repo)*

## License

Apache-2.0 — see [LICENSE](LICENSE).
