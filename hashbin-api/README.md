# hashbin-api

The Java client for the [hashbin.net](https://hashbin.net) timestamping API. Its
**models, a MicroProfile REST Client interface, and the exception plumbing are all
generated at build time from the service's OpenAPI document**, so they cannot drift from
`hashbin-quarkus`.

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
  openapi-generator (java / microprofile, generateSupportingFiles=true)   [GENERATED]
     into ./target/generated-sources (output=. + configOptions.sourceFolder), pruned by
     ./.openapi-generator-ignore (the generator's scaffolding at the module root is discarded):
     - net.hashbin.api.model.*            (StampRequest, TimestampView, TimestampPublicView,
                                           AuthRequest, AuthResponse, MeView)  — Jackson POJOs
     - net.hashbin.api.{Timestamps,Auth,Health,Docs}Api   — @RegisterRestClient interfaces
     - net.hashbin.api.ApiException        — the error the ops declare/throw (extends Exception)
     - net.hashbin.api.ApiExceptionMapper  — @Provider ResponseExceptionMapper: non-2xx -> ApiException
     - net.hashbin.api.RFC3339*            — Jackson java.time helpers (unused here; all timestamps
                                             are modelled as ISO-8601 strings)
  hand-written (the one thing OpenAPI can't express)
     - net.hashbin.api.auth.BearerClientHeadersFactory (adds Authorization: Bearer …)
  bundled into the jar: META-INF/openapi/{openapi.yaml,openapi.json}
```

* **Ordering** is guaranteed by a `provided` reactor dependency on the
  `hashbin-quarkus` `-openapi` artifact — Maven must build the service (which emits the
  spec) before this module. The dependency is content-less (yaml/json only) and
  `provided`, so it adds nothing to this library's runtime classpath and is not published.
  Build from the repository root (`./mvnw install`) so the spec is fresh.
* Because everything is regenerated from the current spec each build, a service
  contract change flows straight here and shows up as a **compile error** in callers.
* The generated `ApiException`/`ApiExceptionMapper` reference `jackson-datatype-jsr310`,
  which arrives `provided` through the spec artifact (Quarkus's own Jackson) — the same
  pattern as the provided Jakarta/MicroProfile API jars: the consumer supplies the impl.

## Usage

The client is the generated MicroProfile REST Client. In a Quarkus app (e.g.
`hashbin-cli`), configure the base URL with `quarkus.rest-client.hashbin.url` (the
`configKey`) and inject it:

```java
@Inject @RestClient TimestampsApi timestamps;

TimestampView ts = timestamps.apiStampPost(
        new StampRequest().hashSha256(sha256).fileSize(size).fileName("report.pdf"),
        null, null);                                    // x-forwarded-for / cf-connecting-ip

List<TimestampPublicView> history = timestamps.apiVerifyGet("auto", sha256);
```

### Auth and error handling

SmallRye OpenAPI hard-filters `Authorization` from the generated parameters, so the bearer
token is **not** a method argument — supply it with a `ClientHeadersFactory`. Each operation
declares `throws ApiException`; the generated `ApiExceptionMapper` (`@Provider`,
`ResponseExceptionMapper<ApiException>`) raises it on any status `>= 400`, carrying the raw
`jakarta.ws.rs.core.Response`:

```java
TimestampsApi api = RestClientBuilder.newBuilder()
        .base(URI.create("https://hashbin.net"))
        .register(new BearerClientHeadersFactory(token))   // omit for anonymous endpoints
        .register(new ApiExceptionMapper())                // non-2xx -> ApiException(getResponse())
        .build(TimestampsApi.class);
```

For CDI injection, register the factory once via `@RegisterProvider` (or a producer); the
`@Provider` `ApiExceptionMapper` is auto-discovered. Anonymous endpoints (`health`, `verify`,
`explore`, unattributed `stamp`) need no token.

## Why `library=microprofile`

The OpenAPI `java` generator's other libraries (`native`, `spring`, `jaxrs-spec`) either
leak an un-generated `ApiClient`/`javax.annotation` imports or pull Spring/Bean-Validation
dependencies into the models. `microprofile` yields clean Jackson POJOs + JAX-RS-annotated
`@RegisterRestClient` interfaces that need only Jakarta/MicroProfile **API** jars
(declared `provided`; the Quarkus consumer supplies the implementation).

## Build & test

```bash
export JAVA_HOME=<a JDK 21>
./mvnw install -DskipTests      # build the service first (emits the spec), then generate + compile this
./mvnw -pl hashbin-api test     # generated models/client + bearer + spec-bundling
```

Tests are plain JUnit (no MicroProfile runtime required): a reflection test asserting the
generated API interfaces carry the expected operations, that the OpenAPI docs are bundled on
the classpath, and the bearer factory's header injection (on/off).

## License

Apache-2.0 — see the [repo LICENSE](../LICENSE).
