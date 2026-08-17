

# ext-rpc

```gradle
    api "tech.krpc.ext:ext-rpc:1.1.0"
```

基于[Quarkus](https://quarkus.io/)的自动扫描注入,以`quarkus-extension`扩展形式提供.

扩展藏考：
https://github.com/quarkiverse


依赖

* **无 `tech.krpc` 运行时依赖**（1.1.0 起）。`rpc-api` / `rpc-client` 均为 `compileOnly`，不进发布 POM。
  客户端能力由构建期探测 `tech.krpc.client.ClientContext` 是否在**消费者运行时 classpath** 上决定：
  在场则装配 RPC client（消费者自带 `rpc-client`，krpc ≥ 1.2.0 的 `rpc-server-quarkus` 已自带）；
  不在场则相关 BuildStep 被 `onlyIf` 跳过。服务端侧一直如此。

## changelogs

* 2026-08-17 (1.1.0) **打包破坏性变更**：发布 POM 清零 `tech.krpc` 依赖——`ext-rpc` 的 `rpc-client`
  与 `ext-rpc-deployment` 的 `rpc-api`/`rpc-client` 全部改为 `compileOnly`。目的是打断
  `rpc-server-quarkus → ext-rpc → rpc-client` 的跨仓发布环，使 krpc 与 ext-rpc 可独立发版。
  **影响**：此前靠 ext-rpc 传递拿到 `rpc-client` 的消费者，需显式声明 `tech.krpc:rpc-client`
  （或升到 krpc ≥ 1.2.0，其 `rpc-server-quarkus` 自带 client）。同时把 `IsClient`/`IsServer`
  的探测从裸 `Class.forName`（探的是 deployment classloader，latent bug，同 EXTRPC-URL-001 病根）
  换为 `QuarkusClassLoader.isClassPresentAtRuntime`；删除死代码 `ClientFactoryMBI`。装配行为未变。

* 2026-07-18 (1.0.5) krpc rpc-* 依赖对齐 1.1.0 → **1.1.1**（OTEL-003 根因修复）。1.1.0 是 pre-OTEL 的 rpc-client，krpc 1.1.1 消费者若不显式强制版本，会静默运行「旧 client / 新 server」组合；将 pin 抬到 1.1.1 消除此偏斜。纯版本 pin，无行为变更（ext-rpc 侧无源码改动，编译/测试对 1.1.1 通过）。native-it 的 `rpc-server-quarkus:1.0.3` 仍为 CI-only 测试脚手架坐标（未动），其传递的 `rpc-common` 由 Gradle 统一上抬至 client pin 1.1.1。

* 2026-07-05 (1.0.4) krpc rpc-* 依赖对齐 1.0.3 → **1.1.0**（消除 LH 观察到的 rpc-client / rpc-common 版本偏斜）。krpc 1.1.0 移除了 `client.ext.ClientConfiguration` / `Remote`（ext-rpc 未引用），并将 `RpcClientFactory.setDefaultCacheManager(Object)` 改为 `setCacheManager(CacheManager)`——`ClientRecorder` 随之适配。native-it 中 rpc-server-quarkus 传递的 `rpc-common:1.0.3` 由 Gradle 统一上抬至 1.1.0，全链无残留偏斜。

* 2026-07-05 (EXTRPC-URL-001) **行为变更**：`quarkus.rpc.client.<app>.url` 由构建期固定改为 **运行时（RUN_TIME）** 配置——环境变量 `QUARKUS_RPC_CLIENT_<APP>_URL` 现可在运行时覆盖拨号地址（JVM 与 native 均生效）；`application.properties` 中的构建期值退化为运行时默认值（无覆盖时仍生效）。原因：URL 曾在构建期消费并烘焙进 native 镜像堆，导致 native 下 env 覆盖被忽略（LH staging 拨到构建期占位符）。实现:拆出 `ClientRuntimeConfig`(RUN_TIME,同前缀 `quarkus.rpc.client`)承载 `<app>.url`,factory/channel 改 RUNTIME_INIT 在 supplier 内按 app 名解析;`scan`/`serial`/`filters` 保持构建期。

* 2026-07-04 (1.0.3) 修复 native augmentation 阶段 DTO 父类走查用 Class.forName 抛 CNFE（父类字段反射注册被静默跳过）：改用 Jandex IndexView 走父类链，正确注册继承字段类型（含泛型基类实参）；非索引外部基类改为 WARN 而非静默。**⚠️ Java 基线 17 → 21**：krpc rpc-* 依赖对齐到正式版 1.0.3（原为 1.0.0.rc1），而 krpc 1.0.3 是 JVM 21 产物，故 ext-rpc 1.0.3 起要求 Java 21（作为 krpc 扩展，消费者本已传递依赖 21 运行时）。新增 native-it 集成 app + native-smoke CI 守卫（继承 DTO 的 native round-trip + 无 CNFE 断言）

* 2026-06-18 升级 Quarkus 3.2.9 → 3.33.2，config root 迁移为 @ConfigRoot + @ConfigMapping 接口

* 2025-12-09 DTO最多支持8层嵌套

* 2023-07-19 更新说明



