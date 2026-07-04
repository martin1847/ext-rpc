

# ext-rpc

```gradle
    api "tech.krpc.ext:ext-rpc:1.0.0"
```

基于[Quarkus](https://quarkus.io/)的自动扫描注入,以`quarkus-extension`扩展形式提供.

扩展藏考：
https://github.com/quarkiverse


依赖 
* rpc-client

## changelogs

* 2026-07-04 (1.0.3) 修复 native augmentation 阶段 DTO 父类走查用 Class.forName 抛 CNFE（父类字段反射注册被静默跳过）：改用 Jandex IndexView 走父类链，正确注册继承字段类型（含泛型基类实参）；非索引外部基类改为 WARN 而非静默。**⚠️ Java 基线 17 → 21**：krpc rpc-* 依赖对齐到正式版 1.0.3（原为 1.0.0.rc1），而 krpc 1.0.3 是 JVM 21 产物，故 ext-rpc 1.0.3 起要求 Java 21（作为 krpc 扩展，消费者本已传递依赖 21 运行时）。新增 native-it 集成 app + native-smoke CI 守卫（继承 DTO 的 native round-trip + 无 CNFE 断言）

* 2026-06-18 升级 Quarkus 3.2.9 → 3.33.2，config root 迁移为 @ConfigRoot + @ConfigMapping 接口

* 2025-12-09 DTO最多支持8层嵌套

* 2023-07-19 更新说明



