# 语言模块管线规格

Status: ready-for-agent
Type: spec

## Problem Statement

NekoJS 的脚本语言处理、模块解析、缓存和执行环境目前容易纠缠在一起：Preparation 可能同时承担编译和模块选择，Resolution/Cache 可能复制编译语义，执行环境又可能吞掉准备阶段错误或原始文件位置。维护者因此难以判断一个语言、模块身份、source map 或缓存失效问题应由哪一层负责。与此同时，JS、ESM、CJS、TS、JSX、TSX 和 Python 都是既有支持范围，GraalJS 和 legacy CJS 路径也不能因重构被静默删除；如果只追求替换编译器或引入更小依赖，又会增加非纯 Java 依赖、语义漂移和发布风险。脚本作者需要的是同一套可追踪阶段：无论本地可信脚本还是远端显式授权脚本，都能看到稳定的模块身份、准确的 source location、可解释的缓存命中和符合既有 reload 语义的失败结果。

## Solution

把语言与模块管线拆成三个 common 内逻辑 Module：Script Preparation 负责从 source、path、extension、requested mode 和 trust-approved source 生成不可变 prepared module；Module Resolution/Cache 负责 CJS/ESM identity、require/import/link、依赖图、缓存命中与失效、legacy CJS bridge 和模块生命周期；Script Execution Environment 负责 Graal Context、Node shim、bindings、managed surface、sandbox/trust、timer/listener/session 以及执行和关闭。三者通过小 Interface 协作，不自动创建 Gradle project 或新的 runtime owner。语言范围全部保留，以可控的纯 Java Implementation 为默认；非纯 Java 转译依赖不进入候选，小型纯 Java 库只有在完整语义 corpus、source-map/diagnostic、许可证、维护状态和体积审查通过后才可替换局部实现。同一公开语义不得长期存在两条并行 pipeline，source map、模块身份、缓存失效、错误阶段和 source location 必须成为 Interface 的可观察部分。

## User Stories

1. 作为脚本作者，我希望 Script Preparation 接收 source、path、extension、requested mode 和 trust-approved source，以便准备阶段有明确输入而不再从全局状态猜测语言和模块模式。
2. 作为维护者，我希望 Script Preparation 输出不可变 prepared module，以便后续 Resolution/Cache 和执行阶段不会反向修改已经准备好的代码。
3. 作为脚本作者，我希望 prepared module 包含 language id、module mode、code 或 IR、source map、诊断位置和稳定 cache key，以便一次准备结果足以解释后续执行和缓存行为。
4. 作为维护者，我希望 Script Preparation 不创建 Graal Context、不决定 HostAccess、不读取 Minecraft，以便纯语言准备可以独立测试和复用。
5. 作为维护者，我希望 Module Resolution/Cache 负责 CJS/ESM identity、require/import/link、依赖图、缓存命中与失效、legacy CJS bridge 和模块生命周期，以便模块语义只有一个责任域。
6. 作为维护者，我希望 Resolution/Cache 消费 prepared module 而不复制编译语义，以便语言转换和模块装载不会形成两套相互漂移的实现。
7. 作为维护者，我希望 Resolution/Cache 不持有平台 callback，以便模块解析不依赖 Minecraft/loader 生命周期或平台事件时机。
8. 作为维护者，我希望 Script Execution Environment 负责 Graal Context、Node shim、bindings、managed surface、sandbox/trust、timer/listener/session 和执行关闭，以便执行态资源的 owner 清晰。
9. 作为脚本作者，我希望执行环境消费 prepared/resolved module 与 frozen Plugin Runtime，以便执行时使用的是已验证的模块结果和冻结的插件契约。
10. 作为脚本作者，我希望执行环境遵守既有 reload 与失败保留语义，以便候选准备、执行或绑定失败时当前 active runtime 仍然可用。
11. 作为维护者，我希望三个 Module 都是 common 内逻辑 Module，而不是自动创建 Gradle project 或第二个 runtime owner，以便职责边界不会转化为新的构建和装配负担。
12. 作为维护者，我希望调用者只跨小 Interface，parser、lowering、cache 和 factory 可以继续作为 Implementation/internal Seam，以便实现细节不会泄漏成公共契约。
13. 作为脚本作者，我希望 JS、ESM、CJS、TS、JSX、TSX 和 Python 继续属于支持范围，以便本次重构不会以内部整理为名删除语言。
14. 作为维护者，我希望 legacy path 和 GraalJS 不被本规格删除，以便迁移、兼容和现有执行语义有明确继续路径。
15. 作为维护者，我希望默认采用可控的纯 Java Implementation，以便语言管线不因引入外部转译器而扩大运行时、许可证和维护风险。
16. 作为维护者，我希望非纯 Java 转译依赖不进入候选，以便依赖选择不会绕过既定的 Java 实现边界。
17. 作为维护者，我希望小型纯 Java 库只有在完整语义 corpus、source-map/diagnostic、许可证、维护状态和体积审查通过后，才可替换局部 Implementation，以便候选评估有统一门槛。
18. 作为维护者，我希望同一公开语义不长期存在两条并行 pipeline，以便修复和验证不会分散到两个实现。
19. 作为脚本作者，我希望 legacy CJS bridge 只作为明确的迁移或兼容输入，并带有删除条件和 characterization，以便它不会永久变成第二套语义。
20. 作为脚本作者，我希望 source map、module identity、cache invalidation、错误阶段和 source location 成为 Interface 的一部分，以便运行时错误仍能追到原始文件和正确模块。
21. 作为维护者，我希望 Graal execution 不吞掉准备阶段或原始文件位置，以便错误发生在哪一层、指向哪个源位置都能被观察。
22. 作为脚本作者，我希望本地可信脚本与远端显式授权脚本走同一套可追踪阶段，以便 trust 决策只影响授权结果而不隐藏语言和模块边界。
23. 作为维护者，我希望拒绝、降级和 reload failure 保留 05 的结果语义，以便管线失败不会破坏 active runtime 或静默产生半成功状态。

## Implementation Decisions

- Script Preparation 的 Interface 输入为 source 或 path、extension、requested mode 和 trust-approved source；输出为不可变 prepared module，至少包含 language id、module mode、code 或 IR、source map、诊断位置和稳定 cache key。
- Script Preparation 不创建 Graal Context，不决定 HostAccess，不读取 Minecraft。其内部 parser、lowering、language frontend、cache key 和静态分析属于 Implementation/internal Seam。
- Module Resolution/Cache 的 Interface 负责 CJS/ESM identity、require/import/link、dependency graph、cache hit/invalidation、legacy CJS bridge 和 module lifecycle。它消费 prepared module，不复制编译语义，也不持有平台 callback。
- Script Execution Environment 的 Interface 负责 Graal Context、Node shim、bindings、managed surface、sandbox/trust、timer/listener/session、execution 和 shutdown。它消费 prepared/resolved module 与 frozen Plugin Runtime，并遵守既有 reload 与失败保留契约。
- 三个 Module 位于 common 内，不自动对应 Gradle project，也不新增 runtime owner。调用者跨小 Interface；内部 parser、lowering、cache 和 factory 可以继续细分，但不提升为公共 SPI。
- JS、ESM、CJS、TS、JSX、TSX 和 Python 全部保留在支持范围。本规格不批准删除语言、legacy path 或 GraalJS，也不修改既有执行语义。
- 默认使用可控的纯 Java Implementation。非纯 Java 转译依赖不进入候选；小型纯 Java 库只有在完整语义 corpus、source-map/diagnostic、许可证、维护状态和体积审查通过后，才可替换局部 Implementation。
- 同一公开语义不得长期存在两条并行 pipeline。legacy bridge 只能是明确的迁移或兼容输入，必须有删除条件并接受 characterization 验证。
- source map、module identity、cache invalidation、错误阶段和 source location 是 Interface 的一部分。Graal execution 不得吞掉准备阶段错误或原始文件位置。
- local trusted source 与 remote explicitly-authorized source 使用同一套可追踪阶段；拒绝、降级和 reload failure 必须保留运行时票定义的 active/candidate 结果语义。
- 本规格不批准替换 compiler、引入候选库、删除语言、改变执行语义或创建新的物理模块。具体 corpus、baseline、候选评估和阶段门禁由验证与发布账本记录。
- 最终 Module 名称、内部类名和源码级迁移不应重新成为规划 blocker；只有在公开契约、真实消费者或测试/发布收益要求时才进入迁移账本。

## Testing Decisions

- 测试必须从最高既有调用者 Seam 观察行为，不测试 parser、lowering、cache 容器或 factory 的内部状态。Preparation 优先通过 `NekoModulePipeline.prepare` 的调用语义验证；Resolution/Cache 优先通过 `NekoScriptModuleLoaderHost` 的 `loadEntry`、`requireFrom`、`nativeImport` 和模块解析调用验证；Execution 优先通过 `ScriptManager` 或 `NekoRuntimeRoot` 的 create、evaluate、reload、close 调用语义验证。
- Preparation 的成功测试应证明每种语言得到正确的 language id、module mode、可执行 code 或 IR、非空且可用的 source map，以及稳定 cache key；失败测试应证明语法或转换错误携带准备阶段和原始 source location，而不是延迟到执行时才丢失位置。prior art 为 `NekoCompilerGoldenTest`、`PythonGoldenTest`、`TypeScriptErasureParseCorpusTest`、`NekoModulePipelinePreflightGateTest` 和 `NekoModulePipelineCacheStampTest`。
- JS、ESM、CJS、TS、JSX、TSX 和 Python 的 corpus 应覆盖固定输入到可执行输出的行为、行结构或 source-map 映射，以及 legacy CJS bridge 的 characterization；测试不得只断言内部函数被调用。`NekoCompilerGoldenTest`、`PythonGoldenTest` 和 `TypeScriptErasureParseCorpusTest` 是既有 golden/corpus 形状。
- Resolution/Cache 的成功测试应证明 CJS/ESM identity 稳定、require/import/link 可执行、循环或依赖图按既有语义处理、命中与失效可观察；失败测试应证明未解析 import、identity 冲突、link 失败或缓存陈旧不会静默成功。prior art 为 `NekoModuleResolverTest`、`NekoScriptModuleLoaderHostSyntaxLocationTest` 和 `ScriptTypeScopedCacheClearTest`。
- source map、module identity 和 cache invalidation 的测试必须从调用者可见结果观察：内容或身份变化会使 cache key 或模块 revision 失效，按 ScriptType 清理只影响目标范围，跨 import 的错误能映射回原始文件。`NekoModulePipelineCacheStampTest`、`NekoScriptModuleLoaderHostSyntaxLocationTest`、`ScriptTypeScopedCacheClearTest` 和 `ScriptReloadRegressionTest` 是 prior art。
- Preparation 与 Resolution/Cache 的测试必须能区分准备失败、解析/link 失败和缓存失败；不得用隐式 compile-on-load 路径掩盖边界。成功是错误阶段和 owner 可解释，失败是错误被归到错误的 Module 或丢失原始 source location。
- Execution Environment 的成功测试应证明 Graal Context、bindings、managed surface、sandbox/trust、timer/listener/session 按既有契约创建和关闭；失败测试应证明候选准备、执行或绑定失败时 active runtime 保持可用，旧资源和监听器不泄漏。prior art 为 `Phase3AFacadeIntegrationTest`、`ScriptReloadRegressionTest` 和 `ReloadMemoryStabilityTest`。
- local trusted 与 remote explicitly-authorized source 的测试应使用同一组可追踪阶段断言，成功是授权结果明确且阶段可见，失败是拒绝、降级或 reload failure 与 05 的结果语义一致。测试复用现有 reload、pack trust 和节点 smoke 资产，不新增全仓测试框架。
- 测试只断言外部可观察行为；普通测试不得更新 corpus golden、source-map golden、缓存基线或 Probe 产物。显式 regenerate 只允许在旧新 diff、原因、影响和维护者审阅记录齐全时进行。

## Out of Scope

**本轮不实施或验证（不构成未来永久禁止）**

- 本轮不修改源码、执行构建或测试、替换 compiler、引入候选库或发布 `1.2.0`。
- 本轮不删除 JS、ESM、CJS、TS、JSX、TSX、Python、legacy CJS bridge 或 GraalJS。legacy CJS bridge 的下线必须等删除条件、characterization、语义与验证充分，并按 07 的 `1.2.0` clean cutover 另行批准；不能现在就删。
- 本规格不改变 HostAccess、高级 Java access、sandbox/trust 或既有 reload/失败保留语义。

**本规格不承诺或明确排除的目标**

- 无删除条件、无 characterization 的 legacy bridge 长期并存，或未经批准直接下线。
- 同一公开语义长期存在两条并行 pipeline。
- 创建新的 Gradle project、runtime owner、公共 parser SPI、通用声明框架或全仓测试框架。
- 冻结最终 Module/类名、具体 cache key 格式、source map 格式、诊断文本或精确内部类布局。
- 重新裁定语言 corpus、baseline、候选库评估或阶段门禁；这些由验证与发布账本承接。

## Further Notes

- 本规格派生自源决策 [自研转译与模块加载应如何拆分且保持语义可控？](../decisions/06-language-module-pipeline.md)。该票的 Resolution 是裁决权威；本文件只是执行视图。
- 相关证据见 [核心运行时与模块边界证据](../evidence/runtime-and-modules.md) 和 [测试、文档、CI 与可观测性证据](../evidence/testing-and-docs.md)；实施顺序与物理落点见 [NekoJS 实施交接单](../implementation-handoff.md)。
- 公开契约与插件模型相关约束见 [脚本表面与插件作者模型如何只有一个事实源？](../decisions/04-public-contract-and-plugin-model.md) 及派生规格 [公开契约与插件模型规格](./04-public-contract-and-plugin-model.md)；运行时 reload 与数据保护见 [运行时所有权、reload 与数据保护的契约是什么？](../decisions/05-runtime-lifecycle-and-data.md)。
- `Status: ready-for-agent` 只表示该规格可作为代理执行视图，不构成源码实施、测试执行、迁移或发布授权。本轮未运行构建、测试或运行时验证。
- 最终命名和源码级迁移不应重新变成规划 blocker；具体 corpus、baseline、候选库评估和阶段门禁由验证与发布账本记录。
