# hashbin-api

The Java client for the [hashbin.net](https://hashbin.net) timestamping API. Its
**request/response models are generated at build time from the service's OpenAPI
document**, so they cannot drift from `hashbin-quarkus`; the HTTP transport and the
HMAC verification are hand-written on top of those generated models.

```xml
<dependency>
  <groupId>net.hashbin</groupId>
  <artifactId>hashbin-api</artifactId>
  <version>1.0.0-SNAPSHOT</version>
</dependency>
```

## How it's built

```
hashbin-quarkus  --(quarkus build: store-schema-directory)-->  target/generated-openapi/openapi.yaml
        |  packaged as the classifier'd  hashbin-quarkus-<v>-openapi.jar  (in-reactor, not published)
        v
hashbin-api:
  maven-dependency-plugin:unpack  ->  target/openapi/openapi.yaml
  openapi-generator (java / microprofile, models only)  ->  net.hashbin.api.model.*   [GENERATED]
  hand-written  ->  net.hashbin.api.HashbinClient (JDK HttpClient), ApiException,
                    net.hashbin.api.security.Signature (HMAC)
```

* **Ordering** is guaranteed by a `provided` reactor dependency on the
  `hashbin-quarkus` `-openapi` artifact — Maven must build the service (which emits
  the spec) before this module. The dependency is content-less (yaml only) and
  `provided`, so it adds nothing to this library's runtime classpath and is never
  deployed. Build from the repository root (`./mvnw install`) so the spec is fresh.
* **Generated models** live in `net.hashbin.api.model` (`StampRequest`,
  `TimestampView`, `TimestampPublicView`, `AuthRequest`, `AuthResponse`, `MeView`)
  under `target/generated-sources/openapi` (not committed). They are plain Jackson
  POJOs with fluent builders.
* **Hand-written** (kept on purpose): the `HashbinClient` (a small JDK `HttpClient`
  wrapper) and `Signature` — the HMAC `sign`/`verify`, which OpenAPI cannot express.

Because the models are regenerated from the current spec every build, a change to a
service response shape flows straight into `hashbin-api` and shows up as a compile
error in callers — no manual DTO upkeep, no dependabot round-trip needed for the
in-repo consumer.

## Usage

```java
HashbinClient client = new HashbinClient(System.getenv("HASHBIN_API_BASE"));

TimestampView ts = client.stamp(
        new StampRequest()
                .hashSha256(sha256)            // compute the digests yourself — this is a contract client
                .hashMd5(md5).hashSha1(sha1).hashSha512(sha512)
                .fileSize(size)
                .fileName("report.pdf"),
        /* token */ null);                      // or a bearer token for attribution

// Confirm the record really came from a server holding the shared HMAC secret:
assert Signature.verify(System.getenv("HASHBIN_HMAC_SECRET"), ts);
```

`HashbinClient` adds the `Authorization: Bearer` header when a token is supplied
(`verify`/`explore`/`health` need none). `ApiException` carries the HTTP `status()`
and the server's `{"error": "..."}` message; transport failures use status `0`.

## Why the models and not the client are generated

The OpenAPI `java`/`microprofile` generator can also emit a MicroProfile REST Client
interface (`generateApis=true`), but it does **not** surface the bearer `Authorization`
header as a method parameter, so authenticated calls would need an extra
`ClientHeadersFactory`. Generating models only keeps this a dependency-light,
plain-Jackson library and lets the hand-written client handle auth and HMAC directly.

## Build & test

```bash
export JAVA_HOME=<a JDK 21>
./mvnw install -DskipTests      # build the service first (emits the spec), then generate + compile this
./mvnw -pl hashbin-api test     # generated models + hand-written client/signature
```

Tests: HMAC pinned to an RFC 4231 vector; signature verify (valid/tampered/wrong
secret) over the generated getters; and a client test that drives `HashbinClient`
against a throwaway JDK stub server.

## License

Apache-2.0 — see the [repo LICENSE](../LICENSE).
