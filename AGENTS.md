# ext-rpc — Agent Guide

Quarkus extension that packages the KRPC RPC **client** (`rpc-client`) as a
`quarkus-extension`: auto-scan discovery and injection of RPC clients, build- and
run-time client config, and native-image support. Artifact `tech.krpc.ext:ext-rpc`
(currently `1.0.4`; `rpc-*` deps at `1.1.0`). Capability-cluster ownership lives in
the umbrella `docs/modules/extensions.md`.

## Scope

- FOR: Quarkus packaging of the krpc rpc-client — CDI auto-injection of RPC clients;
  client configuration (`quarkus.rpc.client.<app>.*`, with `<app>.url` runtime-
  overridable via `QUARKUS_RPC_CLIENT_<APP>_URL`, JVM and native alike); native-image
  correctness (Jandex-driven DTO/inherited-field reflection registration), guarded by
  the `native-it` smoke app + native-smoke CI.
- NOT FOR: core RPC or wire behavior (belongs in `krpc`); server-side runtime; anything
  usable as a standalone product independent of KRPC.
- Tier-1: actively maintained/released, but on concrete krpc need — not speculative
  feature parity.

## Ecosystem rules

- Wire compatibility originates in `krpc` (the reference impl + wire source of truth);
  this repo follows the wire via its `rpc-*` deps, never forks the protocol, never leads
  a wire change. (NS-1 interface-as-contract client; NS-2 wire from krpc.)
- Released on its own cadence, in lockstep with krpc needs — the `rpcVersion` in
  `gradle.properties` is the alignment point.
- Native-image is first-class: every feature must work under AOT/native (closed-world,
  explicit metadata, no open-ended reflection). (NS-7.)
- Long-term direction: umbrella `docs/NORTH_STAR.md` — most relevant here NS-1, NS-2,
  NS-7, NS-8 (Java `ext-*` is main battlefield).

## Build & test

Java 21 baseline (build.gradle `sourceCompatibility 21`); Gradle wrapper 9.6.0.

- `./gradlew tasks` — list tasks / verify the wrapper (verified: runs).
- `./gradlew build` — compile + assemble the extension (not verified).
- `./gradlew test` — unit tests (not verified).
- `./gradlew publishToMavenLocal` — install to `mavenLocal()` for downstream consumption
  (not verified).
- `./gradlew testNative` (in `native-it`) — native round-trip smoke; needs GraalVM
  (not verified).

Note: README's `api "tech.krpc.ext:ext-rpc:1.0.0"` coordinate is stale; the build files
(`version 1.0.4`, `rpcVersion 1.1.0`) are authoritative.

## Discipline

- Behavior changes hide behind a flag, default OFF. No drive-by refactors or format churn.
- Secrets, internal hostnames/IPs, topology never enter the committed tree, logs, or docs.
- Commits stay local until the owner approves a push. No AI signature lines.
