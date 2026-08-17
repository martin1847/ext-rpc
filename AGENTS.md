# ext-rpc — Agent Guide

Quarkus extension that packages the KRPC RPC **client** (`rpc-client`) as a
`quarkus-extension`: auto-scan discovery and injection of RPC clients, build- and
run-time client config, and native-image support. Artifact `tech.krpc.ext:ext-rpc`
(version and `rpc-*` pin: `gradle.properties` is authoritative — do not trust
versions quoted in prose). Capability-cluster ownership lives in
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
- **Invariant (1.1.0+): the published POMs carry ZERO `tech.krpc` dependencies.** `rpc-api` /
  `rpc-client` are `compileOnly`; `rpcVersion` is a compile-time pin only, with no transitive
  effect. This breaks the `rpc-server-quarkus → ext-rpc → rpc-client` cross-repo release cycle,
  so krpc and ext-rpc can be released independently. Both halves of the extension are gated on
  runtime-classpath probes (`RpcProcessor.IsClient` / `IsServer`, via
  `QuarkusClassLoader.isClassPresentAtRuntime`) — never re-promote a `tech.krpc` dep to
  `implementation`/`api` to "fix" a compile error; the consumer supplies it. Guarded by the
  `no-client-it` module (a server-only consumer with rpc-client excluded), which runs in
  `./gradlew build` and as the second native-smoke leg. If it goes red, a client type became
  reachable outside the `onlyIf = IsClient` gate — fix that, do not weaken the guard.
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

Two verification-only modules, never published: `native-it` (consumer WITH rpc-client —
native gRPC round-trip, inherited-DTO reflection, client URL env override) and
`no-client-it` (consumer WITHOUT rpc-client — the zero-dependency invariant above).

## Releasing

`docs/RELEASING.md` — the two-gate Central flow, the irreversible step, and the traps
(gitignored wrapper, `~/.m2` mirror blocking Central, credential handling). Read it
before publishing anything.

Note: README install coordinates may lag releases; `gradle.properties` is
authoritative.

## Discipline

- Behavior changes hide behind a flag, default OFF. No drive-by refactors or format churn.
- Secrets, internal hostnames/IPs, topology never enter the committed tree, logs, or docs.
- Commits stay local until the owner approves a push. No AI signature lines.
