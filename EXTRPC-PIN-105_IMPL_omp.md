# EXTRPC-PIN-105 — ext-rpc 1.0.5 (rpc-* pin 1.1.0 → 1.1.1)

Owner: omp · Branch `release/1.0.5-pin` (cut from ext-rpc origin/main @ 6224f65) ·
Reviewer: codex (light) · Commits LOCAL, no push.

Motivation: **OTEL-003 root cause.** ext-rpc pinned `rpc-client` at 1.1.0 (pre-OTEL).
Every krpc 1.1.1 consumer that pulls ext-rpc transitively silently ran an
**old-client / new-server** mix unless it force-overrode the version. Maintainer
approved 1.0.5 with the client pin at 1.1.1.

Scope: version pin bump + changelog + findings. **No behavior changes, no refactors.**

## What was changed

| File | Change |
|------|--------|
| `gradle.properties` | `version = 1.0.4 → 1.0.5`; `rpcVersion = 1.1.0 → 1.1.1` |
| `README.md` | Added 1.0.5 changelog entry (top of `## changelogs`); bumped the trivially-present install coordinate `api "tech.krpc.ext:ext-rpc:1.0.0" → :1.0.5` (was long-stale README debt; task explicitly permitted the trivial version-coord update) |

No source code touched. `ext-rpc/build.gradle` and `ext-rpc-deployment/build.gradle`
consume rpc-* purely via `$rpcVersion`, so the single gradle.properties bump propagates
to every published module.

## Hardcoded rpc-* coordinate scan (task item 2)

Grep across poms/build files/templates/docs for hardcoded rpc-* coordinates:

- **All published-module rpc-* deps use `$rpcVersion`** (`ext-rpc`, `ext-rpc-deployment`)
  — no hardcoded coord; bump propagates automatically.
- **`native-it/build.gradle:36` hardcodes `tech.krpc:rpc-server-quarkus:1.0.3`** — the ONLY
  hardcoded rpc-* version coordinate in the tree. **Deliberately NOT bumped.** Rationale:
  - `native-it` is a CI-only integration-test scaffold (`include 'native-it'` in
    settings.gradle; root build skips its publish wiring — it never enters the Central
    bundle, never published).
  - It resolves `rpc-server-quarkus:1.0.3` only to give the native image a server to
    compile against; its transitive `rpc-common`/`rpc-api` are **uplifted by Gradle to the
    client pin (now 1.1.1)** because `native-it` also declares `rpc-client:${rpcVersion}`.
    This is the same documented mechanism as the 1.0.4 cycle (README 1.0.4 entry:
    "native-it 中 rpc-server-quarkus 传递的 rpc-common:1.0.3 由 Gradle 统一上抬至 1.1.0").
  - Bumping the server coord would be a behavior change to test scaffolding (out of scope)
    and is unnecessary for the pin's correctness. **Reported, not touched.**
- README `## changelogs` mentions of `1.1.0`/`1.0.3` are historical prose — left as-is.
- `docs/orchestration/*.md` mentions of `1.0.0`/`1.0.1` etc. are prior-cycle findings
  reports — historical record, not live coordinates; left as-is.

## Build / test evidence (task item 3)

Environment: `/opt/gradle/gradle/bin/gradle` (Gradle 9.6.0) on Oracle GraalVM 25.0.3
(project baseline JDK 21; wrapper is gitignored 8.0 and fails on 21, per repo gotcha).
Resolution: `mavenLocal()` then `mavenCentral()` (no ci-init override needed — 1.1.1
resolved cleanly; no 404/mirror fallthrough hit this run).

**Dependency resolution — full chain aligned at 1.1.1, no skew:**
```
tech.krpc:rpc-client:1.1.1
  +--- tech.krpc:rpc-common:1.1.1
  |    +--- tech.krpc:rpc-api:1.1.1
  +--- tech.krpc:rpc-api:1.1.1
```
(`gradle :ext-rpc-deployment:dependencies --configuration runtimeClasspath`)

**`gradle build --max-workers=2`: BUILD SUCCESSFUL** (compile + assemble both extension
modules + native-it JVM fast-jar quarkusBuild).

**`gradle clean test --max-workers=2`: BUILD SUCCESSFUL** — all tests re-run against 1.1.1:

| Suite | tests | failures | errors |
|-------|:---:|:---:|:---:|
| `RpcProcessorNestDtoTest` | 4 | 0 | 0 |
| `ClientUrlBuildTimeFallbackTest` | 1 | 0 | 0 |
| `ClientUrlRuntimeOverrideTest` | 1 | 0 | 0 |
| `MultiClientUrlResolutionTest` | 1 | 0 | 0 |
| **total** | **7** | **0** | **0** |

**Compatibility verdict: no breakage against rpc-* 1.1.1.** ext-rpc's client-facing
surface (`RpcClientFactory`, `CacheManager` in `ClientRecorder`; `@RpcService`/`Doc`
annotations; `RpcResult`) compiled and the EXTRPC-URL-001 runtime-override guards passed
unchanged. 1.1.1 introduced no API break affecting ext-rpc (the 1.0.4-era
`setCacheManager(CacheManager)` migration remains valid).

## NOT validated (honest gaps)

- **native-it `testNative`** (GraalVM native round-trip: continued-DTO reflection + no-CNFE
  + env-override native guard) was **NOT run** — it requires a GraalVM native build and runs
  in the **native-smoke CI job only**. `gradle build` exercised native-it's JVM fast-jar path
  (`quarkusBuild` succeeded) but not the native compile. This matches the established
  release process (native validated in CI, not locally this session).
- **Downstream cross-repo consumer smoke** (krpc consumers re-resolving ext-rpc 1.0.5) is an
  orchestrator/post-publish step, not part of this pin task.
- **Central publish** is the ORCHESTRATOR's next step — not touched here (no publishing run).

## Deliverables

- `gradle.properties`: version 1.0.5, rpcVersion 1.1.1.
- `README.md`: 1.0.5 changelog entry + install coord bump.
- This findings doc.
- Single local commit (no push, no AI signature).
