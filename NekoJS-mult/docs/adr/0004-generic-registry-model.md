# 通用注册表模型：三层解耦 + 先攒后建

现状是 13 个逐类型手写的 `XxxRegistryEventJS`（其中 11 个严格同模板复制）直接持有 loader 类型（如 `ItemRegistryEventJS` 第 25 行持有 NeoForge `RegisterEvent`），分发集中在一处 13 分支 if-else。PR #37 提出通用注册表方向，RE2（`research/re2-precedents` §3）精读 KubeJS 验证了成熟形态。本 ADR 定核心模型：

1. **三层解耦**（KubeJS `RegistryEventHandler → RegistryKubeEvent → RegistryObjectStorage` 同构）：
   - **平台适配层**：每 loader 一个，订阅 loader 注册事件（NeoForge `RegisterEvent` / Fabric 寄存器），活在版本树守卫内；
   - **平台无关事件 `RegistryEventJS`**：脚本 API 面，**零 loader import**（PR #37 硬要求，Fabric 移植前提）；
   - **平台无关 builder 仓库**：先攒后建——脚本期只攒 builder，对象创建放平台注册事件回调以 `Supplier` 注册。
2. **引擎不做跨注册表拓扑排序**：信任 loader 事件序（NeoForge 自己就是人工维护的顺序表 `GameData.getRegistrationOrder`——连 NeoForge 都不做通用拓扑排序）。
3. **RegistryInfo 发现 = 贡献式扫描根**：扫描根类由各平台 adapter **贡献**（NeoForge 贡献 `net.minecraft.core.registries.Registries.class`，Fabric 贡献自己的根），引擎反射扫描根类的 `ResourceKey` 字段生成 `RegistryInfo`——版本差异（1.21.1 / 26.x 字段集不同）天然自适应；模组自定义注册表由插件经 `registry_infos` 扩展点补充（merge = **append**：贡献的是扫描类/信息条目，无键冲突）。
4. **ObjectType 注册 = `registry_types` EP 上的普通方法**：`registerType(resourceKey, name, factory)` + `setDefaultType(resourceKey, name)`；脚本端 `event.custom(id)` 不带类型名走 default（KubeJS 体验关键）。类型层冲突 merge = **overrideWarn**（内置类型可被第三方覆盖 + warn——addon 生态的实际运转方式）。
5. **对象层（同 id 重复注册）= fail-fast**：注册内容 id 冲突直接抛错。
6. **事件生命周期三阶段**：① 脚本期**收集**（builder 只进仓库）→ ② 平台注册事件**抽干**（按 loader 序，对象以 Supplier 注册）→ ③ **after-all 后置阶段**（"读已完成注册表再派生"的需求挂这里，如按已注册方块生成挖掘等级；连带注册的统一后置回调框架在此，其 Builder 侧 API 归 ADR-0005/R2）。

## Considered Options

- PR #37 的 try-with-resources `scope(Registries.ITEM)`：只是分组语法糖，多一个概念，弃——普通分组方法 + default 达到同等体验。
- `additionalBuilders()` 直返集合（PR #37 方案一）：回调式拿到"注册目标"抽象、支持条件派生，优于直返集合——细化归 R2。
- 引擎内注册表拓扑排序：弃（见决策 2）。

## Consequences

- `wrapper/event/registry` 层从此零 loader import——可用 import 检查守护（自动化验收归 G1 裁定）。
- 存量 13 个 `XxxRegistryEventJS` + 15 个 `XxxBuilderJS` 的替换迁移归 G1 编排；脚本 API 兼容映射归 R3。
- 两个注册表扩展点（`registry_infos` / `registry_types`）成为 V2 扩展点系统（ADR-0001~0003）的首批真实消费者。
- **实现修订（P2 落地）**：连带派生条目的投递以「目标注册表自身的 pass」为准（KubeJS `createAdditionalObjects` 同款）——源注册表 pass 时收集进仓库，目标注册表 pass 时以 Supplier 注册；目标 pass 已过的派生条目报错跳过。after-all 阶段保留给"读已完成注册表再派生"类钩子，以及未消化内容的 load-complete 诊断。
