# hashbin-api

A **plain-Java client** for the [hashbin.net](https://hashbin.net) trusted
file-timestamping API. No Quarkus — just Jackson and the JDK `HttpClient` — so any
JVM consumer can depend on it as a versioned Maven artifact and have a backend
contract change surface as a compile error, not a runtime surprise.

```xml
<dependency>
  <groupId>net.hashbin</groupId>
  <artifactId>hashbin-api</artifactId>
  <version>1.0.0-SNAPSHOT</version>
</dependency>
```

## What it covers

Every `/api` route, mapped to typed calls:

| Method | Route |
|---|---|
| `health()` | `GET /api/health` |
| `stamp(StampRequest, token)` | `POST /api/stamp` |
| `verify(hash, algorithm)` | `GET /api/verify` |
| `myTimestamps(token)` | `GET /api/my-timestamps` |
| `explore(limit, offset)` | `GET /api/explore` |
| `register(email, password)` / `login(...)` | `POST /api/auth/{register,login}` |
| `me(token)` | `GET /api/auth/me` |

Plus `Signature`, which verifies a returned record's `server_signature` by
recomputing `HMAC-SHA256(secret, "{hash_sha256}:{created_at}")` — the trust
guarantee the service provides, and the part an OpenAPI spec cannot express.

## Usage

```java
HashbinClient client = new HashbinClient(System.getenv("HASHBIN_API_BASE"));

TimestampRecord ts = client.stamp(
        StampRequest.builder()
                .sha256(sha256)   // compute the digests yourself — this is a contract client, not a file hasher
                .md5(md5).sha1(sha1).sha512(sha512)
                .fileSize(size)
                .fileName("report.pdf")
                .build(),
        /* token */ null);

// Confirm the record really came from a server holding the shared HMAC secret:
assert Signature.verify(System.getenv("HASHBIN_HMAC_SECRET"), ts);
```

`ApiException` carries the HTTP `status()` and the server's `{"error": "..."}`
message; transport failures use status `0`.

## Design notes

- **Hand-written, not OpenAPI-generated.** The current spec does not carry typed
  request/response bodies, and the security-critical HMAC/auth behaviour lives
  outside OpenAPI entirely, so codegen would produce a thin client you'd re-type
  by hand anyway. Tightening the spec (a `StampRequest` DTO + `@Schema` response
  annotations on the service side) is the prerequisite if you ever want to
  generate the models.
- **Decoupled from the server by contract.** This module has no dependency on
  `hashbin-quarkus`; it mirrors the wire format. That duplication is intentional —
  it is what lets the artifact be versioned and pinned independently.

## Build & test

```bash
export JAVA_HOME=<a JDK 21>
./mvnw -pl hashbin-api test     # from the repo root
```

Tests are plain JUnit: an HMAC vector pinned to RFC 4231, signature verify
(valid / tampered / wrong-secret), and a client test that drives the real
`HashbinClient` against a throwaway JDK stub server — no live backend required.

## License

Apache-2.0 — see the [repo LICENSE](../LICENSE).
