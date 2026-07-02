# IMPL — NATIVE-002: server-side native support in ext-rpc (omp)

> Worktree `/Users/martin/Garden/wt-extrpc-native`, repo `ext-rpc`, branch
> `feat/native-server-provider` (off `origin/main` @6c1f6b7). Commits LOCAL only, no push.
> Reviewer: codex (heterogeneous). Roadmap SoT: workspace `docs/roadmap/active-roadmap.md`
> NATIVE-002. Consumer truth being deleted: krpc `SPEC.md` §13.2/§13.3.

## Result

A krpc+ext-rpc consumer native-builds and boots to gRPC serving with **zero per-service
native glue** — no `NettyServerProviderFeature` copy, no `NativeGrpcProviderConfig`
reflection holder, no `quarkus.class-loading.removed-resources` strip. Proven end-to-end
by native-building krpc `test-server` against the local `-native-SNAPSHOT` with all three
consumer workarounds absent (below).

Two local commits on `feat/native-server-provider`:

- `6ccc0cc feat(native): register server-side grpc providers for native`
- `68f7f78 fix(native): gate ext-rpc epoll substitution on grpc-common absence`

## What changed

### 1. Server-side grpc provider registration (deployment + runtime)

- **New `ext-rpc/.../runtime/graal/NettyServerProviderFeature.java`** — a GraalVM
  `Feature` whose `beforeAnalysis` forces `io.grpc.ServerProvider.provider()`, baking the
  server provider list into the image heap (Quarkus builds native with
  `-H:-UseServiceLoaderFeature`, so the ServiceLoader path is off). Mirrors krpc's
  *client* `GraalvmBuild` which does the same for `ManagedChannelProvider` /
  `NameResolverRegistry` / `LoadBalancerRegistry`. Lives in ext-rpc **runtime** because a
  Feature class must be on the native-image analysis classpath.
- **`ext-rpc-deployment/.../RpcProcessor.java`** — new `@BuildStep(onlyIf = IsServer.class)`
  `regGrpcServerProvidersForNative` producing:
  - `ReflectiveClassBuildItem` for the 5 grpc provider impls
    (`NettyServerProvider`, `NettyChannelProvider`, `UdsNettyChannelProvider`,
    `io.grpc.internal.PickFirstLoadBalancerProvider`, `io.grpc.internal.DnsNameResolverProvider`)
    with `.constructors(true).methods(false).fields(false)` — the no-arg constructors are
    what grpc's runtime `ServiceLoader` needs to instantiate them.
  - `NativeImageFeatureBuildItem("tech.krpc.ext.runtime.graal.NettyServerProviderFeature")`
    — wires the Feature for every consumer, replacing the per-service `--features=` flag.
  - new `IsServer` `BooleanSupplier` (delegates to existing `isServer()`), sibling of the
    existing `IsClient`.

### 2. Stale substitution → gated, not deleted

- **`ext-rpc/.../runtime/graal/GrpcNettySubstitutions.java`** rewritten:
  - deleted the `static { ServerProvider.provider(); }` block (server registration now owned
    by the Feature, for all consumers, not tied to this class being in the image);
  - added `onlyWith = QuarkusGrpcCommonAbsent.class` to `@TargetClass(io.grpc.netty.Utils)`;
    the predicate is a build-time classpath probe (`getClass().getClassLoader().loadClass(
    "io.quarkus.grpc.common.runtime.graal.Target_io_grpc_netty_Utils")` → present ⇒ false),
    the exact idiom Quarkus uses in `NoDomainSocketPredicate`.

## Per-layer decisions (which build item, why)

- **ServerProvider baking → `NativeImageFeatureBuildItem` (a Feature), NOT
  `RuntimeInitialized`/`BuildTimeInitialized`.** The requirement is *side-effecting eager
  init at analysis time* (call `ServerProvider.provider()` so `ServerRegistry`'s hard-coded
  candidate list is populated into the heap). That is precisely what a `Feature.beforeAnalysis`
  does and what the proven downstream used. `BuildTimeInitializedClassBuildItem` only marks a
  class `--initialize-at-build-time` — it does not *call* the provider lookup, so the registry
  would still be empty. `NativeImageFeatureBuildItem` ships the Feature by FQN (String ctor),
  so `ext-rpc-deployment` needs neither the graal SDK nor grpc on its own compile classpath.
- **Provider impls → `ReflectiveClassBuildItem` with `constructors(true)`.** ServiceLoader
  reflectively `newInstance()`s them; the Builder defaults `constructors=true` (verified by
  `javap` of the bytecode), set explicitly for intent. `methods`/`fields` off — not needed.
- **Server-gated (`onlyIf = IsServer.class`).** A client-only consumer already has its
  provider lists baked by krpc's client `GraalvmBuild`; the server registration is pure
  overhead there and semantically wrong. Gate matches the existing `IsClient` steps.
- **`META-INF/services/io.grpc.*` → NOT kept via `NativeImageResourceBuildItem`.** The goal
  flagged this as conditional ("if the runtime ServiceLoader path is used"). It is not: the
  Feature bakes the provider list into the heap at build time, and the proven downstream
  (ledger-server) shipped **no** `quarkus.native.resources.includes` (verified — grep of its
  resources returns nothing; its `application.properties` has only `removed-resources` +
  `--features=`). The successful test-server build below confirms the resource include is
  unnecessary. Skipped to avoid dead config.

## Load-bearing static block (`ServerProvider.provider()` + epoll) — the key finding

The old `Target_io_grpc_netty_Utils` in ext-rpc did **two** jobs; the replacement splits them
and I verified each is fully covered:

1. **`static { ... ServerProvider.provider() }` (server registration).** → Moved to
   `NettyServerProviderFeature.beforeAnalysis()`, wired for **every** server build by
   `ext-rpc-deployment` (not just when this substitution class lands in the image). This is
   strictly more reliable: the old path only ran if native-image happened to initialize the
   `Utils` target, whereas a registered Feature always runs in `beforeAnalysis`. Confirmed in
   the build log: `tech.krpc.ext.runtime.graal.NettyServerProviderFeature: Registers io.grpc
   server-side provider (NettyServerProvider) for native image`, and the runner boots with
   **no `ProviderNotFoundException`**.

2. **epoll disable (`isEpollAvailable()->false`, `getEpollUnavailabilityCause()->null`).**
   → Covered two ways depending on the consumer:
   - **grpc-common present** (ledger-server + the 8 downstream, via
     quarkus-opentelemetry→vertx-grpc): Quarkus ships
     `io.quarkus.grpc.common.runtime.graal.Target_io_grpc_netty_Utils` that is
     **behavior-identical** — I decompiled it: `isEpollAvailable` = `iconst_0/ireturn`
     (`false`), `getEpollUnavailabilityCause` = `aconst_null/areturn` (`null`). So Quarkus's
     authoritative version fully covers the epoll job; ext-rpc's must step aside to avoid the
     duplicate-substitution abort. The `onlyWith` gate does exactly that.
   - **grpc-common absent** (krpc's own `test-server`: has `quarkus-netty` but no
     grpc-common — verified in its `build.gradle`): nothing else substitutes
     `io.grpc.netty.Utils`, so ext-rpc's substitution is **load-bearing** and the gate keeps
     it active. This is why the decision is **gate, not delete**: deleting outright would break
     the native build of any raw-io.grpc consumer without grpc-common.

**Delete-vs-gate rationale (guardrail):** the goal permitted delete *if* nothing is
load-bearing outside Quarkus. It is load-bearing (test-server case), so I gated. ext-rpc is a
Quarkus extension (quarkus plugin + quarkus-arc dep), so "non-Quarkus consumer" is not a real
axis here; the real axis is grpc-common presence, which the predicate keys on precisely.

## What ran (verification)

1. **`gradle build -x test`** in the worktree → `BUILD SUCCESSFUL`. (System `gradle` 9.6.0
   on GraalVM JDK 25; repo has no wrapper. Pre-existing deprecation warnings only.)
2. **Publish to mavenLocal** at `tech.krpc.ext:ext-rpc:1.0.1-native-SNAPSHOT` (+
   `:ext-rpc-deployment:...`) via `gradle publishToMavenLocal -Pversion=1.0.1-native-SNAPSHOT`
   — version bump **not committed** (CLI override only). `-SNAPSHOT` also lets Gradle's signing
   plugin skip signing. Confirmed the snapshot jar carries all three graal classes and
   `deployment-artifact=tech.krpc.ext:ext-rpc-deployment:1.0.1-native-SNAPSHOT`.
3. **True consumer native build** — krpc `test-server` (SPEC.md §12 recipe, JDK-21 Mandrel
   builder per §13.4), container build on orbstack docker:
   ```
   gradle :test-server:build --init-script /tmp/native002-consumer-test.init.gradle \
     -Dquarkus.native.enabled=true -Dquarkus.native.container-build=true \
     -Dquarkus.native.builder-image=quay.io/quarkus/ubi9-quarkus-mandrel-builder-image:jdk-21 \
     -Dquarkus.package.jar.enabled=false -x test
   ```
   The init script (throwaway, `/tmp`, **no krpc file touched**) forced only:
   (a) `tech.krpc.ext:ext-rpc{,-deployment}` → `1.0.1-native-SNAPSHOT` (test-server pulls
   ext-rpc transitively via ext-mybatis:1.0.1; `-SNAPSHOT` sorts *below* 1.0.1 so a force is
   required); (b) `io.grpc:*` → `1.79.0` (NATIVE-001 skew, separate item, expected & allowed
   by the goal). **Consumer workarounds all absent** — no Feature class, no reflection holder,
   no `removed-resources`.
   Result: `BUILD SUCCESSFUL in 1m13s`, native-image gen `1m0s`. Log shows my step ran
   (`=== [ ext-rpc native ] registered 5 grpc provider impls + ServerProvider feature`),
   the Feature applied, DTO reflection ran (`=== [ 3 RpcService ] : BookService,DemoService,DemoRpc`),
   and **no** `conflicts with previously registered` abort (gate worked — test-server has no
   grpc-common).
4. **Boot the runner** (ARM aarch64 Linux ELF) in a UBI9 container (`ubi9/ubi-minimal`; the
   `quarkus-micro-image:2.0` base has too-old glibc for the Mandrel jdk-21 output — GLIBC_2.34
   not found — a base-image note, not a defect):
   ```
   RpcServer expose 2 services on 50061
   test-server 1.0.2 native (powered by Quarkus 3.33.2) started in 0.029s.
   Installed features: [agroal, cdi, ext-mybatis, ext-rpc, hibernate-validator,
                        jdbc-mysql, narayana-jta, smallrye-context-propagation]
   ```
   BookService (3 methods) + DemoService (18 methods) = **21 methods** registered, gRPC bound
   on **50061** (P4 precedent port), boot **0.029s** (≈ the 0.028s precedent), **ext-rpc**
   feature active, **no `ProviderNotFoundException`**. (Reaching this required only runtime
   config that a real deploy always sets — `rpc.server.app`, `rpc.server.port`, a valid
   `rpc.server.jwks`, and `QUARKUS_DATASOURCE_*` to dodge the §13.4 SIGSEGV guard — none of
   which is native glue.)

## What was NOT verified / caveats

- **NATIVE-001 grpc skew was force-pinned, not fixed.** The `io.grpc→1.79.0` force in the
  throwaway init script is expected per the goal (separate roadmap item). ext-rpc itself
  carries no such force; a consumer still needs it until NATIVE-001 lands.
- **Only test-server (no grpc-common) was natively booted.** The grpc-common branch of the
  gate (ledger-server et al.) is verified by decompilation (byte-identical Quarkus
  substitution) and by the collision mechanism, **not** by a fresh native build of a
  grpc-common consumer this session.
- **JVM mode / wire behavior unchanged** — no runtime code touched beyond the build-time-only
  graal classes; the Feature and gated substitution are inert at JVM runtime.
- **krpc repo left untouched** — throwaway wiring was an `--init-script` in `/tmp`; `git status`
  in krpc shows only pre-existing SPEC/docs edits (not mine). Test container removed. The
  `test-server/build/**-runner` artifact is gitignored.

## Follow-ups (out of scope here; for the umbrella)

- On release of this ext-rpc version, **delete krpc `SPEC.md` §13.2 and §13.3** and the
  §13.6 checklist lines for them (roadmap acceptance) — that edit is in the krpc repo, not
  this one.
- Downstream services can drop `NettyServerProviderFeature`, `NativeGrpcProviderConfig`,
  their `--features=` arg, and the `removed-resources` strip once they bump to this version.
