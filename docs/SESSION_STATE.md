# Project status (2026-11-05)

A quick "where things stand" snapshot. Full rationale, decisions and gotchas live in
[`HANDOFF.md`](HANDOFF.md); the human-facing docs are in [`../README.md`](../README.md).

## Sibling repos

- **hashbin-quarkus** — the backend (Quarkus 3.20.1 / JDK 21). Dev/test database via Quarkus Dev
  Services (a throwaway PostgreSQL started by Testcontainers). A file-activated `web` Maven profile
  serves `../hashbin-web/dist` same-origin. The native container-image build is wired (Docker
  extension + Mandrel container build). 50 tests, green.
- **hashbin-web** — dependency-free static frontend (vanilla TypeScript + `tsc`). `dist/` is
  committed; `./build.sh` self-bootstraps the pinned Node; CI builds the image from source.
- **hashbin-deploy** — Kubernetes (Kustomize) manifests for the api + web + database.

## Recently completed

- **Native container image** for `hashbin-quarkus` (`README.md` → "Container image"). The native
  binary compiles inside a Mandrel container, so no local GraalVM is needed. The image runs
  non-root (UID 185) on port 8080.
- **Self-contained Jenkins CI** (`ci/Jenkinsfile`): builds + runs the full test suite in a pod with
  a DinD sidecar, then builds and pushes the native image. Added the Maven wrapper (`./mvnw`) and a
  no-op `allTests` Maven profile that the pipeline uses.
- Fixed native-mode response serialization: the response DTOs are `@RegisterForReflection` (the
  JVM-only test suite passes without it but the native image would return empty bodies).

## Next steps

- Pin the deployed image by `digest:` in the `hashbin-deploy` production overlay (it currently
  retags by version).
- Hardening follow-ups: `quarkus-smallrye-health` for real liveness/readiness probes (the current
  k8s probes hit the static `/api/health`); metrics-driven autoscaling; CloudNativePG backups →
  object storage; edge rate-limiting (the app's limiter is per-instance).

## Build prerequisites

- **JDK 21** on `JAVA_HOME` — required; Quarkus 3.20's Hibernate build fails on newer JDKs.
- A container engine (Docker, or rootless Podman) reachable by Testcontainers, for Dev Services and
  the native image build.
- **Node ≥ 20** only to rebuild `dist/` from source; the committed `dist/` and CI's build-from-source
  both avoid needing a local Node.

## Locked decisions

- Separate repos (not a monorepo); the `web` Maven profile is the same-origin dev bridge.
- Deploy: `hashbin-deploy` · Kustomize · CloudNativePG · nginx web pod · single-origin `/api`
  routing · native api image.
- Container image built with the **Docker** extension (not Jib), native; the native toolchain runs
  in a Mandrel container so no local GraalVM is required.
