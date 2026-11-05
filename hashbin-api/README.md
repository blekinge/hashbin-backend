# hashbin-api

The Java client for the [hashbin.net](https://hashbin.net) timestamping API. Its
**models and a MicroProfile REST Client interface are generated at build time from the
service's OpenAPI document**, so they cannot drift from `hashbin-quarkus`. The HMAC
signature verification is hand-written on top (it isn't expressible in OpenAPI).

```xml
<dependency>
  <groupId>net.hashbin</groupId>
  <artifactId>hashbin-api</artifactId>
  <version>1.0.0-SNAPSHOT</version>
</dependency>
```

## What's generated vs hand-written

```
hashbin-quarkus  --(quarkus build: store-schema-directory)-->  target/generated-openapi/openapi.yaml
        |  packaged as the classifier'd  hashbin-quarkus-<v>-openapi.jar  (in-reactor, not published)
        v
hashbin-api:
  maven-dependency-plugin:unpack  ->  target/openapi/{openapi.yaml,openapi.json}
  openapi-generator (java / microprofile)        [GENERATED, into target/, not committed]
     - net.hashbin.api.model.*            (StampRequest, TimestampView, TimestampPublicView,
                                           AuthRequest, AuthResponse, MeView)  — Jackson POJOs
     - net.hashbin.api.{Timestamps,Auth,Health,Docs}Api   — @RegisterRestClient interfaces
  hand-written
     - net.hashbin.api.security.Signature            (HMAC sign/verify)
     - net.hashbin.api.auth.BearerClientHeadersFactory (adds Authorization: Bearer …)
     - net.hashbin.api.HashbinClient, ApiException    (a dependency-free JDK-HttpClient client)
  bundled into the jar: META-INF/openapi/{openapi.yaml,openapi.json}
```

* **Ordering** is guaranteed by a `provided` reactor dependency on the
  `hashbin-quarkus` `-openapi` artifact — Maven must build the service (which emits the
  spec) before this module. The dependency is content-less (yaml/json only) and
  `provided`, so it adds nothing to this library's runtime classpath and is not published.
  Build from the repository root (`./mvnw install`) so the spec is fresh.
* Because everything is regenerated from the current spec each build, a service
  response-shape change flows straight here and shows up as a **compile error** in callers.
* **Nice-to-have:** the exact `openapi.yaml` + `openapi.json` are packaged at
  `META-INF/openapi/`, so a consumer of this jar always has the spec it was built from.

## Using the generated MicroProfile REST client

In a Quarkus app (e.g. `hashbin-cli`), inject or build the generated interface. The bearer
token is supplied via `BearerClientHeadersFactory` — SmallRye OpenAPI hard-filters
`Authorization` from the parameters, so it is **not** a method argument; the factory adds
the header instead.

```java
// Per-instance token:
TimestampsApi api = RestClientBuilder.newBuilder()
        .base(URI.create("https://hashbin.net"))
        .register(new BearerClientHeadersFactory(token))   // omit for anonymous endpoints
        .build(TimestampsApi.class);

TimestampView ts = api.apiStampPost(new StampRequest().hashSha256(sha).fileSize(size), null, null);
assert Signature.verify(System.getenv("HASHBIN_HMAC_SECRET"), ts);
```

Or with CDI: `@Inject @RestClient TimestampsApi api;` and configure the base URL via
`quarkus.rest-client.hashbin.url` (the `configKey`), registering the factory as needed.

## Dependency-free alternative: `HashbinClient`

`HashbinClient` is a small JDK `HttpClient` wrapper over the same generated models that
adds `Authorization: Bearer` itself (no MicroProfile runtime required) — handy in tests
or non-Quarkus JVMs:

```java
HashbinClient client = new HashbinClient("https://hashbin.net/api");
TimestampView ts = client.stamp(new StampRequest().hashSha256(sha).fileSize(size), token);
```

`ApiException` carries the HTTP `status()` and the server's `{"error": "..."}` message;
transport failures use status `0`.

## Why `library=microprofile`

The OpenAPI `java` generator's other libraries (`native`, `spring`, `jaxrs-spec`) either
leak an un-generated `ApiClient`/`javax.annotation` imports or pull Spring/Bean-Validation
dependencies into the models. `microprofile` yields clean Jackson POJOs + JAX-RS-annotated
`@RegisterRestClient` interfaces with only Jakarta/MicroProfile **API** jars (declared
`provided`; the Quarkus consumer supplies the implementation).

## Build & test

```bash
export JAVA_HOME=<a JDK 21>
./mvnw install -DskipTests      # build the service first (emits the spec), then generate + compile this
./mvnw -pl hashbin-api test     # generated models + client + signature
```

Tests are plain JUnit: an HMAC vector pinned to RFC 4231; signature verify
(valid/tampered/wrong-secret) over the generated getters; a `HashbinClient` test against a
throwaway JDK stub server; and a reflection test asserting the generated API interfaces
exist with the expected operations, the spec is bundled, and the bearer factory injects the
header — all with no MicroProfile runtime.

## License

Apache-2.0 — see the [repo LICENSE](../LICENSE).
