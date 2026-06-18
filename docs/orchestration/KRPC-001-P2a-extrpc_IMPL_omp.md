# IMPL — KRPC-001 Phase 2a: ext-rpc → Quarkus 3.33.2 (omp)

> Worktree `/Users/martin/Garden/wt-ext-rpc-q33`, repo `middleware/ext-rpc`,
> branch `feat/quarkus-3.33` (cut from latest local `main`). Commit LOCAL only.
> Reviewer: codex. Cross-repo prerequisite for krpc Phase 2 augmentation.

## Result

`ext-rpc` + `ext-rpc-deployment` build green on Quarkus **3.33.2**, republished to
mavenLocal at `tech.krpc.ext:ext-rpc:1.0.0` (+ `:ext-rpc-deployment:1.0.0`). The krpc
`:test-server:quarkusAppPartsBuild` augmentation now gets **PAST** the ext-rpc
`ClientConfig` config-root error; it next fails on `tech.krpc.mybatis.runtime.MyConfig`
(ext-mybatis), which is Phase 2a-mybatis and out of scope here.

## Audit — what the config-root actually was (H3 resolved)

The goal noted a `main` search did not surface `@ConfigRoot`/`ClientConfig`. That was
stale: this worktree (cut from latest `main`) has it at
`ext-rpc/src/main/java/tech/krpc/ext/runtime/ClientConfig.java`, and the published
1.0.0 jar matches it (`META-INF/quarkus-config-roots.list` = `tech.krpc.ext.runtime.ClientConfig`).
There is **one** config root (`ClientConfig`) plus **one** nested `@ConfigGroup`
(`ServerApp`, embedded via `Map<String,ServerApp> apps`). No "published-from-older-commit"
drift.

**Resolved property prefix = `quarkus.rpc.client`** (load-bearing). Old class root was
`@ConfigRoot(name = "rpc.client", ...)` with the `prefix` attribute left at its 3.2.9
default `"quarkus"` (verified by `javap` of `io.quarkus.runtime.annotations.ConfigRoot`
in `quarkus-core-3.2.9.Final`). Full namespace = `prefix + "." + name` = `quarkus.rpc.client`.
Confirmed against the 3.33 precedent: `ThreadPoolConfig` (old name `thread-pool` →
`@ConfigMapping(prefix="quarkus.thread-pool")`) and `ApplicationConfig`
(`quarkus.application`, same `BUILD_AND_RUN_TIME_FIXED` phase). The interface migration
preserves this prefix exactly, so any real consumer using `quarkus.rpc.client.*` keeps binding.

## Breaking API jump 3.2.9 → 3.33.2 (verified by javap, not memory)

- `@ConfigRoot` in 3.33 dropped `name` + `prefix` — only `phase` remains. Class-based
  roots are rejected.
- `io.quarkus.runtime.annotations.ConfigItem` was **removed** from quarkus-core 3.33.
- Config roots must be **interfaces** + `@ConfigRoot(phase=...)` +
  `io.smallrye.config.@ConfigMapping(prefix=...)`. `@ConfigItem(name=PARENT)` →
  `@WithParentName`; `@ConfigItem(defaultValue=...)` → `@WithDefault`; doc annotations
  (`@ConfigDocMapKey`, `@ConfigDocSection`, `@ConfigGroup`) survive in
  `io.quarkus.runtime.annotations`. `@WithParentName`/`@WithDefault` live in
  `io.smallrye.config.*` (on compile classpath via quarkus-core's compile-scope dep on
  smallrye-config).
- Interfaces cannot provide `default` `Object` methods → `ClientConfig.toString()` and
  `ServerApp.toString()` were dropped; `ServerApp.isMatch(String)` became a `default`
  method using `scan()`.
- Deployment field access → accessor calls (compile-forced): `config.apps`→`config.apps()`,
  `host.url`→`host.url()`.
- Two build-script breakages surfaced by 3.33's stricter `validateExtension` (3.2.9 was
  lenient / published 1.0.0 anyway):
  1. Root `build.gradle` applied `io.quarkus.extension` to the aggregator → root
     `validateExtension` failed looking for a `deployment` project. Set `apply false`
     (matches the pre-existing `// apply false` comment; the plugin is applied per-subproject).
  2. `ext-rpc/build.gradle` `deploymentModule = 'deployment'` ≠ real project
     `ext-rpc-deployment` → fixed to `'ext-rpc-deployment'`.
  3. `ext-rpc-deployment` declared `quarkus-arc-deployment` as `compileOnly`; 3.33's
     extension dependency verification requires the matching `*-deployment` artifacts
     (`quarkus-arc-deployment`, transitively `quarkus-core-deployment`) on the deployment
     module's dependency graph → promoted to `implementation`.
- Build items (`MultiBuildItem`), recorder bridge POJOs, graal substitutions, and the
  `ReflectiveClassBuildItem.builder().methods().fields()` / `SyntheticBeanBuildItem` /
  `NativeImageProxyDefinitionBuildItem` deployment APIs compiled unchanged on 3.33.2.

## Config root before → after

`ClientConfig`:
```
- @ConfigRoot(name = "rpc.client", phase = ConfigPhase.BUILD_AND_RUN_TIME_FIXED)
- public class ClientConfig {
-     @ConfigDocSection @ConfigDocMapKey("server-app-name") @ConfigItem(name = ConfigItem.PARENT)
-     public Map<String, ServerApp> apps;
-     @ConfigItem public Map<String, Set<String>> filters;
-     @Override public String toString() { ... }
+ @ConfigRoot(phase = ConfigPhase.BUILD_AND_RUN_TIME_FIXED)
+ @ConfigMapping(prefix = "quarkus.rpc.client")
+ public interface ClientConfig {
+     @ConfigDocSection @ConfigDocMapKey("server-app-name") @WithParentName
+     Map<String, ServerApp> apps();
+     Map<String, Set<String>> filters();
```

`ServerApp`:
```
- @ConfigGroup public class ServerApp {
-     @ConfigItem public String url;
-     @ConfigItem(defaultValue = "JSON") public String serial;
-     @ConfigItem public Set<String> scan;
-     @Override public String toString() { ... }
-     public boolean isMatch(String rpcClass) { for (var s : scan) ... }
+ @ConfigGroup public interface ServerApp {
+     String url();
+     @WithDefault("JSON") String serial();
+     Set<String> scan();
+     default boolean isMatch(String rpcClass) { for (var s : scan()) ... }
```

The `public static final String GLOBAL` constant and the large commented-out mybatis
blocks were preserved verbatim (pre-existing, not mine to delete).

## Files changed (git diff vs main — Quarkus-scoped only, 8 files)

- `gradle.properties` — `quarkusMiniSupport` 3.2.9.Final → 3.33.2
- `build.gradle` — root quarkus-extension plugin `apply false`
- `ext-rpc/build.gradle` — `deploymentModule = 'ext-rpc-deployment'`
- `ext-rpc-deployment/build.gradle` — `quarkus-arc-deployment` compileOnly → implementation
- `ext-rpc/.../runtime/ClientConfig.java` — class → interface @ConfigRoot+@ConfigMapping
- `ext-rpc/.../runtime/ServerApp.java` — @ConfigGroup class → interface
- `ext-rpc-deployment/.../deployment/ClientProcessor.java` — field → accessor
- `ext-rpc-deployment/.../deployment/RpcProcessor.java` — field → accessor

## Verified (with `QUARKUS_DATASOURCE_PASSWORD` unset)

- `gradle clean build` → BUILD SUCCESSFUL, both modules, Quarkus 3.33.2 (gradle 8.5,
  GraalVM JDK 21, source/target 17). Warnings are pre-existing (Gradle config-doc-gen
  disabled note; `ClientRecorder` deprecated `new URL()` + raw `Class`).
- `gradle publishToMavenLocal` → BUILD SUCCESSFUL; both `signMavenJavaPublication` ran
  (global signing keys present). Artifacts overwritten:
  `~/.m2/repository/tech/krpc/ext/ext-rpc/1.0.0/ext-rpc-1.0.0.jar`,
  `~/.m2/repository/tech/krpc/ext/ext-rpc-deployment/1.0.0/ext-rpc-deployment-1.0.0.jar`.
- `javap` of the published jar: `ClientConfig` is `interface` with `apps()`/`filters()`,
  `@ConfigMapping(prefix="quarkus.rpc.client")`, `@ConfigRoot(BUILD_AND_RUN_TIME_FIXED)`;
  `ServerApp` is `@ConfigGroup interface` with `@WithDefault("JSON")` + `default isMatch`;
  `quarkus-extension.properties` deployment-artifact = `tech.krpc.ext:ext-rpc-deployment:1.0.0`.
- Cross-repo smoke: cleared gradle cache (`~/.gradle/caches/.../tech.krpc.ext/*` +
  metadata, per `refresh.sh`), then in `/Users/martin/Garden/wt-krpc-gradle9`
  (`extRpcVersion=1.0.0`, `quarkusMiniSupport=3.33.2`):
  `QUARKUS_DATASOURCE_PASSWORD= ./gradlew :test-server:quarkusAppPartsBuild` re-resolved
  the fresh jars and got **PAST** the `ClientConfig` ConfigMapping error. New failure:
  `tech.krpc.mybatis.runtime.MyConfig must be an interface annotated with @ConfigRoot and
  @ConfigMapping` (ext-mybatis, expected next phase). `quarkus.datasource.password=`
  rendered empty in the worker — no secret leaked.

### How to re-confirm krpc passes the ext-rpc gate

```
rm -rf ~/.gradle/caches/modules-2/files-2.*/tech.krpc.ext/*
rm -rf ~/.gradle/caches/modules-2/metadata-2.*/descriptors/tech.krpc.ext/*
cd /Users/martin/Garden/wt-krpc-gradle9
QUARKUS_DATASOURCE_PASSWORD= ./gradlew :test-server:quarkusAppPartsBuild
# expect: no longer "ClientConfig must be an interface ..."; next error is MyConfig (ext-mybatis)
```

## NOT verified

- Native image build (JVM fast-jar augmentation only; graal substitutions untouched but
  not exercised).
- Runtime binding of `quarkus.rpc.client.*` keys — test-server sets none, so the
  config-mapping bind path is not exercised end-to-end. Prefix correctness rests on the
  3.2.9-vs-3.33 annotation analysis above, not a live bind.
- Full `test-server` boot / `@QuarkusTest` — env-gated on MySQL (out of scope, untouched).
- ext-mybatis migration (Phase 2a-mybatis).

## OPEN DECISION (for Martin — do NOT action now)

Republishing **the same coordinate** `tech.krpc.ext:ext-rpc:1.0.0` overwrote the existing
local 1.0.0; fine for local validation. For a **formal release**, decide whether ext-rpc
(and ext-mybatis) should **rev the version** (e.g. a new `1.x`) to avoid overwriting an
already-published 1.0.0 coordinate in aliyun/central. If rev'd, krpc's `extRpcVersion`
(currently `1.0.0` in `wt-krpc-gradle9/gradle.properties:6`) must be wired to the new
version. Version number left unchanged here pending that call.
