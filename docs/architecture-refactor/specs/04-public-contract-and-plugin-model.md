# 公开契约与插件模型规格

Status: ready-for-agent
Type: spec

## Problem Statement

NekoJS 同时面向整合包脚本作者和 Java 插件作者，但公开面存在多个容易混淆的观察物：managed Script API 有规范契约、反射输入和派生输出，legacy catalog/preview 仍承担迁移观察；Java 插件侧既有 Point 收集模型，也有 `NekoJSPlugin` 作者门面。维护者如果不先确定每个域的唯一事实源，就可能把 manifest、Probe、声明文件或 legacy catalog 误当成第二规范源，把作者便利投影误当成第二 registry，或者用隐式能力状态掩盖平台差异。脚本作者也可能因为契约整理而意外失去 KubeJS 风格入口、高级 Java 访问或既有 Graal interop 能力。插件作者则需要一个小而可追踪的扩展核心，让 Point、Contributor、Hook、依赖、冻结、结果和句柄之间的责任清晰，并让真实 addon 的发现、加载和错误行为可以被验证。

## Solution

按域建立单一事实源和清晰的派生关系。managed Script API 以 `NormativeApiContract` 为规范源，facade、数据类型和事件注册是反射输入，manifest、Probe、TypeScript declaration 与 Python declaration 是派生或观测物；`NekoScriptCatalog` 和 `LEGACY_PREVIEW` 只用于迁移观察，不自动获得 stable 身份。Java 插件扩展以 Point 文件为收集、依赖、合并、冻结、生命周期和 result/Handle 的事实源，`NekoJSPlugin` 的 Hook 与 Contributor 只是作者投影。公开能力必须显式声明为 `supported`、`partial` 或 `unavailable`，并带上 loader、版本和运行上下文条件。KubeJS 风格入口、`java:`、`Java.type`、`Java.loadClass`、Graal interop 和当前 HostAccess 能力在本次重构中保持不变；这不等同于 `1.2.0` 全面兼容，公开 breaking 仍按一次 clean cutover 进入迁移表。不新增独立 API artifact，也不建立万能 catalog、第二注册路径或非必要框架。Builder 的显式 setter 与 JavaBean-style property 必须进入同一条校验、规范化、定义指纹和事务收集路径。

## User Stories

1. 作为维护者，我希望 managed Script API 只有一个规范源，以便修改契约时不必同时猜测反射输入、JSON、声明文件和 legacy catalog 中哪一份才是权威。
2. 作为维护者，我希望 facade、数据类型和事件注册成为 `NormativeApiContract` 的反射输入，以便公开脚本表面的变化能被契约反射统一捕获。
3. 作为维护者，我希望 manifest、Probe、TypeScript declaration 和 Python declaration 明确标记为派生或观测物，以便它们用于比较、发布和编辑体验时不会反向成为第二规范源。
4. 作为维护者，我希望 `NekoScriptCatalog` 和 `LEGACY_PREVIEW` 只承担迁移观察职责，以便既有 legacy symbol 的出现不会自动被理解为 managed stable 承诺。
5. 作为脚本作者，我希望 legacy preview 中的符号不会因为出现在 catalog 里就自动升级为 stable，以便我能区分迁移可见性和正式兼容承诺。
6. 作为维护者，我希望每个脚本能力都显式标为 `supported`、`partial` 或 `unavailable`，以便跨 loader、跨版本和跨上下文的能力差异不会靠静默行为猜测。
7. 作为脚本作者，我希望不支持的脚本能力明确失败或明确拒绝，而不是静默 no-op，以便我能知道当前平台究竟执行了什么。
8. 作为脚本作者，我希望 KubeJS 风格便利入口继续保留，同时 `1.2.0` 仍可按一次 clean cutover 引入公开 breaking，以便便利面不被删除但迁移表仍能明确旧写法到新写法的变化。
9. 作为脚本作者，我希望 `java:`、`Java.type`、`Java.loadClass`、Graal interop 和当前 HostAccess 能力继续可用，以便高级 Java 访问不会因契约纯化被意外收紧。
10. 作为 Java 插件作者，我希望 Point 文件成为扩展收集、依赖、合并、冻结、生命周期和 result/Handle 的事实源，以便新增或修改扩展时只需追踪一条主链。
11. 作为 Java 插件作者，我希望 `NekoJSPlugin` 的 Hook 和 Contributor 是 Point 的作者投影，而不是第二 registry，以便便利门面和底层收集语义不会各自漂移。
12. 作为 Java 插件作者，我希望普通插件优先复用已有 binding、event、adapter、type-doc、recipe 和 contribution 面，以便没有真实收集生命周期时不必制造新 Point。
13. 作为维护者，我希望只有真实收集生命周期、隔离差异或产物依赖出现时才新增 Point，以便扩展点数量继续由需求驱动而不是由框架冲动驱动。
14. 作为插件作者，我希望 Plugin Runtime 按 discovery、contribution、dependency ordering、freeze、initialization/collection、finish/result 的可观察顺序运行，以便我能预测贡献何时生效。
15. 作为插件作者，我希望 `dependsOn`、环依赖、未知依赖和未完成数据依赖都有明确错误，以便错误顺序不会退化为静默漏注册。
16. 作为插件作者，我希望冻结后的扩展契约不能隐式改变，以便已发布的 collection、merge、lifecycle 和 result 语义保持稳定。
17. 作为插件作者，我希望通过 Extension Handle 或类型化 result 获取完成后的产物，以便调用者不必读取原始 pointId 到 Object 的映射。
18. 作为维护者，我希望错误、依赖和 reload 语义由 Plugin Runtime 统一负责，以便各 Point 或作者门面不会各自实现一套失败处理。
19. 作为脚本作者，我希望 Builder 的显式 setter 与 JavaBean-style property 产生相同的规范化结果，以便两种写法不会造成配置漂移。
20. 作为维护者，我希望 Builder 的两种写入方式进入同一条校验、定义指纹和事务收集路径，并且不通过 public field 或启动期 `drain()` 旁路，以便收集状态和全局状态不会被污染。
21. 作为插件作者，我希望 `api.*` 继续承载作者入口、脚本 surface 契约、插件契约和必要的 Graal interop facade，以便公开命名空间保持可理解且不因“纯度”拆出无收益 artifact。
22. 作为维护者，我希望现有 FQCN 只有在 `1.2.0` 迁移表、真实消费者和测试或发布收益同时成立时才评估重排，以便命名整理不会单独扩大重构范围。

## Implementation Decisions

- managed Script API 的唯一规范源是 `NormativeApiContract`。facade、数据类型和事件注册类是其反射输入；manifest、Probe、TypeScript declaration 和 Python declaration 是从已验证契约或冻结 surface 派生的观测物。
- `NekoScriptCatalog` 与 `LEGACY_PREVIEW` 继续作为迁移观察面。legacy symbol 不得因为被 catalog 收录而自动升级为 managed stable contract，也不得建立第二套规范源。
- Script surface 的每项能力必须显式标记为 `supported`、`partial` 或 `unavailable`，并记录适用的 loader、版本和运行上下文条件。能力不可用时必须显式拒绝、降级或报告，不得静默 no-op。
- KubeJS 风格便利入口继续保留。`java:`、`Java.type`、`Java.loadClass`、Graal interop 和当前 HostAccess 能力不在本次重构中收紧。
- Point 文件是扩展收集、依赖、合并、freeze、lifecycle、result 和 Handle 的事实源。`NekoJSPlugin` 的 Hook 与 Contributor 是该事实源的作者投影，不是第二 registry。
- 普通插件优先使用已有 binding、event、adapter、type-doc、recipe 和 contribution 面。只有存在真实收集生命周期、隔离差异或产物依赖时，才新增 Point。
- Plugin Runtime 的可观察顺序固定为 discovery、contribution、dependency ordering、freeze、initialization/collection、finish/result。freeze 后不得隐式改变已经发布的契约。
- Plugin Runtime 统一负责错误、`dependsOn` 和 reload 语义。环依赖、未知依赖、重复扩展点 id、冻结后注册和未完成数据依赖必须可诊断地失败。
- Extension Handle 和类型化 result 是插件调用者读取完成产物的接口；调用者不依赖原始 pointId 到 Object 的映射，也不从静态可变全局读取结果。
- Builder 的显式 setter 与 JavaBean-style property 是同一写入语义。两者必须进入同一条校验、规范化、定义指纹和事务收集路径，不得通过同名 public field 绕过校验，也不得复用会污染启动期全局状态的构建路径。
- `api.*` 保留为作者入口、脚本 surface 契约、插件契约和必要 Graal interop facade 的命名空间。engine-internal Implementation 留在 core/runtime 域，MC/loader wrapper 留在共享实现或对应节点。
- common（含 `api.*`）允许使用 GraalJS，但不得引入 Minecraft/loader 类型或依赖；MC/loader-facing 共享实现留在共享实现或对应节点。不为隔离 Graal 抽 DTO、adapter 或独立 API artifact。
- 不新增独立 API artifact。现有 FQCN 不因命名纯度单独搬迁；只有 `1.2.0` 迁移表、真实消费者以及测试或发布收益同时成立时，才评估命名重排。
- public signature、capability matrix、迁移表和最终实现顺序由验证与发布账本承接；本规格只固定已闭合的契约方向，不冻结具体源码移动、最终类名或具体事件名。

## Testing Decisions

- 测试必须从最高既有调用者 Seam 观察行为，不测试 bootstrap 私有循环、反射器内部状态或容器对象身份。managed surface 的优先 Seam 是 `CoreManagedApiBootstrap`、`ApiSurfaceBootstrap`、冻结后的 `ApiSurfaceSnapshot` 以及 manifest/declaration 输出；插件面的优先 Seam 是 `NekoPluginBootstrap`、`NekoPluginRuntime` 和作者可见的 `NekoJSPlugin`、Point、Contributor、Handle。
- managed contract 的成功测试应证明反射输入经过验证后生成确定性 surface，manifest 与声明只包含有意承诺的 symbol、signature、module 和 capability；失败测试应证明 legacy binding 阴影、贡献不匹配契约或 removed/added/changed symbol 会被拒绝。prior art 为 `CoreManagedApiBootstrapTest`、`ApiSurfaceBootstrapTest`、`ManagedApiEnvironmentTest`、`ApiManifestGoldenTest`、`ManagedApiDeclarationGeneratorTest` 和 `ApiSurfaceSnapshotTest`。
- legacy 与 managed 双轨的成功测试应证明 legacy preview 仍可用于迁移观察，但不会进入 managed collection 或获得 stable 身份；失败测试应证明把 legacy catalog 当作规范源或让同名 symbol 静默覆盖 managed 契约时测试变红。prior art 为 `ApiSurfaceBootstrapTest` 和 `ApiSurfaceSnapshotTest`。
- 插件配对的成功测试应证明每个作者 Hook 都有唯一 Point、每个内置点都在配对表内且签名稳定；失败测试应证明遗漏、重复、未配对 `register*` 钩子或收集器声明位置错误会立即失败。prior art 为 `PluginHookPairingTest`。
- 插件生命周期和错误的成功测试应证明依赖会按拓扑序重排、冻结结果可经 Handle/result 读取且每轮 bootstrap 隔离；失败测试应证明环依赖、未知依赖、未声明数据依赖、重复 id 和 freeze 后注册都在约定阶段失败并带有可定位路径。prior art 为 `NekoPluginBootstrapV2Test`、`NekoPluginExtensionPointTest`、`NekoPluginExtensionProviderTest`、`NekoPluginBootstrapHookDeliveryTest` 和 `DeepFreezeTest`。
- Builder 的成功测试应从脚本可见的 typed Builder 调用观察结果，证明显式 setter 与 property assignment 生成相同规范化和定义指纹；失败测试应证明 public field 旁路、启动期 `drain()` 污染或两种写法产生不同事务结果会被拒绝。prior art 为 `BuilderTagTest` 和 `Phase3AFacadeIntegrationTest` 的 Graal 集成形状。
- 外部 addon 的验收必须在真实 loader discovery、fat jar 依赖、插件启动、Hook/Point 配对、Probe 输出和 reload 上完成，而不是只复用内部类实例化。现有 `NekoPluginExtensionProviderTest`、`ProbeOutputCompatibilityTest`、`LegacyProbeTreeTest` 和节点 smoke 资产提供测试形状；真实 addon fixture 是已记录缺口，不得用内部单测冒充。
- capability 测试应通过 loader/version/context 下的外部行为断言 `supported`、`partial`、`unavailable`，成功是声明与真实可用性一致，失败是静默 no-op、错误 capability 或缺失条件说明。测试复用 managed surface、Probe 和节点 smoke 的 contract/golden 类型，不新增测试框架。
- 所有测试只断言外部可观察行为；普通测试不得更新 golden、manifest、Probe 基线或声明产物。任何基线更新必须走显式的旧新 diff、原因、迁移影响和维护者审阅流程。

## Out of Scope

**本轮不实施或验证（不构成未来永久禁止）**

- 本轮不修改源码、移动文件、执行构建或测试、发布 `1.2.0` 或执行数据迁移。
- 本规格不收紧 GraalJS、HostAccess、`java:`、`Java.type`、`Java.loadClass` 或高级 Java interop 的现有能力；未来能力变更须另行裁定。
- 本规格不冻结最终 FQCN、包布局、具体事件名、payload、Builder 方法全集或精确 public signature。

**本规格不承诺或明确排除的目标**

- 新增独立 API artifact、万能 catalog、第二注册路径、通用 capability 框架或新的测试框架。
- 承诺 `1.2.0` 全面兼容或既有脚本无需迁移；便利入口保留不等于取消一次 clean cutover 的公开 breaking 与迁移表。
- 在本规格中决定 capability matrix 的具体单元格值、平台 parity 结论、迁移窗口或最终发布顺序。
- 把 Builder 的最终字段列表、开放 registry 类型或多人同步协议纳入本规格；这些仍按 08 与 07 的闭合边界执行。

## Further Notes

- 本规格派生自源决策 [脚本表面与插件作者模型如何只有一个事实源？](../decisions/04-public-contract-and-plugin-model.md)。该票的 Resolution 是裁决权威；本文件只是执行视图。
- 相关证据见 [API、扩展点与脚本表面证据](../evidence/apis-and-extensions.md) 和 [测试、文档、CI 与可观测性证据](../evidence/testing-and-docs.md)；实施顺序与物理落点见 [NekoJS 实施交接单](../implementation-handoff.md)。
- 语言与模块管线相关约束见 [自研转译与模块加载应如何拆分且保持语义可控？](../decisions/06-language-module-pipeline.md) 及派生规格 [语言模块管线规格](./06-language-module-pipeline.md)；运行时 reload 与数据保护见 [运行时所有权、reload 与数据保护的契约是什么？](../decisions/05-runtime-lifecycle-and-data.md)。
- Builder 的单一写入语义和 typed Builder 边界见 [搬运功能如何适配 NekoJS 事件面与运行时扩展？](../decisions/08-ported-features-event-surface.md)；本规格只复用其已闭合语义，不重新裁定最终字段或方法名。
- `Status: ready-for-agent` 只表示该规格可作为代理执行视图，不构成源码实施、测试执行、迁移或发布授权。本轮未运行构建、测试或运行时验证。
- 最终命名和源码级迁移不应重新变成规划 blocker；若确需公开重排或迁移，按验证与发布账本的迁移表、真实消费者和测试收益条件处理。
