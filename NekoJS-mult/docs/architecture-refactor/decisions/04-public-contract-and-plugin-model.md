# 脚本表面与插件作者模型如何只有一个事实源？

Status: closed
Type: grilling
Mode: HITL
Parent: [NekoJS 架构重构路线图](../../architecture-refactor-map.md)
Assignee: session-0ccab587-1d9c-436f-b53b-a6428bdb1aaf (主 agent，与维护者共同裁决)
Blocked by: [维护者的最小理解范围与目标模块归属如何确定？](01-maintainer-module-design.md)

## Resolution

### 已确认前提

`common-api` 已并入 `common`，维护者便利优先。`common`（含 `api.*`）允许使用 GraalJS，但禁止引入 Minecraft/loader 类型或依赖；MC/loader-facing 共享实现放在根 `src/` 或对应 node。不为隔离 Graal 而抽 DTO、adapter 或另一个 API jar。

### Script API contract

1. `NormativeApiContract` 是 managed Script API 的规范源；facade、data type 和 event registration 是其反射输入，manifest、Probe 和 TypeScript/Python declaration 是派生/观测物。
2. `NekoScriptCatalog` 与 `LEGACY_PREVIEW` 是迁移观察面，不是稳定规范源；legacy symbol 不得因为出现在 catalog 中就升级为 managed stable contract。
3. Script surface 的 capability 必须显式标为 `supported`、`partial` 或 `unavailable`，并带 loader/version/context 条件；不以静默 no-op 假装跨平台 parity。
4. KubeJS 风格便利入口继续保留；高级 `java:`、`Java.type`、`Java.loadClass` 和 Graal interop 是明确支持的能力。本次重构不借“契约纯化”收紧它们。

### Java Plugin contract

1. Point 文件是扩展收集、依赖、merge、freeze、lifecycle、result/Handle 的事实源；`NekoJSPlugin` hook/Contributor 是作者投影，不是第二 registry。
2. 普通插件优先使用已有 binding、event、adapter、type-doc、recipe 和 contribution 面；只有真实收集生命周期、隔离差异或产物依赖才新增 Point。
3. Plugin Runtime 的可观察顺序是 discovery -> contribution -> dependency ordering -> freeze -> initialization/collection -> finish/result；freeze 后不得隐式改变已发布 contract。错误、dependsOn 和 reload 语义由 Plugin Runtime 统一负责。
4. `PluginHookPairingTest`、contract validation 和 managed surface tests 是 public contract 的机械验证点；不把固定文件数或包布局当成契约。

### api.* 命名与 artifact

`api.*` 保留为作者入口、脚本 surface contract、插件 contract 和必要的 Graal interop facade 的命名空间；engine-internal Implementation 留在 `core.*`/runtime 域，MC/loader wrappers 留在 `src/` 或 node。现有 FQCN 不因“纯度”单独搬迁；只有 `1.2.0` 迁移表、真实消费者和测试/发布收益同时成立时，才评估命名重排。

本票不新增独立 API artifact，也不批准具体源码移动。public signature、capability matrix、迁移表和最终实现顺序由 07 的验证/发布账本承接。

## Question

脚本表面与插件作者模型如何只有一个事实源？

需要在便利、可见性和契约稳定性之间确定一套可维护的作者模型。问题维度包括：

- KubeJS 风格便利脚本入口与 raw 高级 Java 访问如何分层，managed、legacy、probe 三种运行或发现语义如何表达且不制造第二套事实源。
- Point、hook、Contributor 的最小形式是什么；脚本可见能力如何与当前 loader、版本、运行阶段中的真实可用性保持一致。
- Java 插件的小核心契约与 capability 方向如何落成可追踪的契约，哪些能力必须显式声明、发现、拒绝或降级。
- 外部发布 API jar 是否真的需要；若需要，契约、参数类型、版本闭包、构建产物和消费者范围的闭合条件是什么，若不需要如何防止再次拆出无收益 API 模块。

证据入口：[APIs evidence](../evidence/apis-and-extensions.md)、[proposal.md](../proposal.md)。[ADR-0001](../../adr/0001-extension-point-model-v2.md) 与 [ADR-0010](../../adr/0010-plugin-authoring-model.md) 是历史模型，必须在本票中显式重开并比较，而不是把 Collector 外围形态、双形态作者面或既有 Point 组织当作已确认答案。
