# NekoJS 验证与迁移规格

Status: ready-for-agent

Type: spec

## Problem Statement

本次架构重构横跨五个节点、多个运行时域、公开脚本与插件契约、持久化数据和发布流程。规划虽然已经闭合，但如果没有一份统一、可执行、可审计的验证与迁移视图，实施很容易退化为按目录批量搬运：部分功能只完成编译却没有平台能力证据，失败候选污染旧 active 状态，普通测试悄悄改写 golden，必要的数据格式变化缺少回读与回滚，性能阈值在取得基线前被凭空设定，或者把 Fabric processor 的延期误写成能力已经等价。

维护者、脚本作者、插件作者和发布负责人需要看到同一组阶段边界、产物、退出条件与失败诊断。每一行功能覆盖账本都必须能追到 owner、输入、产物和删除条件；每个节点能力都必须由真实实现和证据裁定；所有工作都只服务一次 `1.2.0` clean cutover，而不是把兼容层、双运行时路径或历史候选重新带入长期维护。

## Solution

以 P0-P4 和 W0-W10 作为同一次 `1.2.0` clean cutover 的内部执行视图，不分别产生公开 breaking，不在本轮先改版本，也不保留长期 shim。P0 冻结可重复基线与输入；P1 收拢唯一 runtime owner；P2 深化运行时逻辑 Module；P3 逐功能域和平台能力验证，不重回历史方案 C；P4 完成删除、迁移、回滚和发布交接。

解决方案把验证分成 contract、golden、runtime smoke、data fixture、artifact/source trace 和维护者四类试做，并按节点支持等级设定不同 gate。能力只使用 `supported`、`partial`、`unavailable` 三个值；`未验证` 与 `deferred` 作为独立维度记录，不能偷偷改写能力结论。golden 只能通过显式生成和审阅更新；可选离线 validator 默认只读且不是硬 release gate；性能是独立 P0 基线，阈值在 P4 前依据数据决定。

## User Stories

1. 作为维护者，我希望 P0-P4 与 W0-W10 被明确视为同一次 `1.2.0` clean cutover，以便实施不会把内部阶段误当成多次公开发布。
2. 作为发布负责人，我希望本轮只确定 `1.2.0` 的 cutover 方向而不立即修改版本，以便版本号、迁移表和最终产物在同一发布窗口一致。
3. 作为维护者，我希望每个阶段都有输入、必须留下的证据和退出条件，以便阶段完成可以由产物证明，而不是由口头进度证明。
4. 作为维护者，我希望最终只保留一套标准实现，以便长期兼容 shim、旧 route 和双运行时路径不会沉淀成新的维护债。
5. 作为脚本作者，我希望每个公开 breaking 都进入迁移表，以便在 `1.2.0` 前能逐项找到旧写法、新写法和数据影响。
6. 作为插件作者，我希望插件契约变化同样有迁移说明，以便 Point、Hook、Contributor、Handle 和构建依赖的变化可以安全升级。
7. 作为测试负责人，我希望普通测试只能读取 golden，只有显式 regenerate 才能更新基线，以便日常测试不会把当前结果自动变成新标准。
8. 作为维护者，我希望每次 golden 更新都同时提交旧新 diff、原因、受影响功能、迁移影响和审阅记录，以便契约变化可追溯。
9. 作为维护者，我希望 golden 只冻结有意承诺的公开行为，而不冻结私有 helper、目录布局或对象身份，以便重构内部实现时仍能改善结构。
10. 作为平台负责人，我希望能力矩阵只使用 `supported`、`partial`、`unavailable` 三个值，以便能力状态有稳定、可比较的语义。
11. 作为平台负责人，我希望 `未验证` 和 `deferred` 作为独立维度记录，以便缺少测试、尚未执行和明确不支持不会互相冒充。
12. 作为维护者，我希望任何节点的 `partial` 或 `unavailable` 都被显式记录，以便平台差异不会被静默 no-op 伪装成 parity。
13. 作为发布负责人，我希望 primary、secondary 和 experimental 节点按各自等级执行构建、制品、契约、能力与 smoke gate，以便支持等级与实际验证强度一致。
14. 作为功能负责人，我希望每个 coverage ledger 行都逐项闭合，以便事件、recipe、client、network、command、diagnostics、pack trust 等功能域不会因局部通过而整体宣布完成。
15. 作为维护者，我希望 P3 继续承担功能与平台验证，而不是回到历史方案 C，以便已选定的目标结构不被重新开放为无证据的替代路线。
16. 作为数据所有者，我希望 config、world、实体与玩家 pdata、脚本与 pack、trust-store、用户编辑 workspace/declaration 和历史日志在验证完成前不被覆盖或删除，以便升级失败时原始数据仍可恢复。
17. 作为运维人员，我希望必要的数据迁移采用备份或原子替换、schema/version 标记、旧 fixture 回读、幂等验证和失败恢复，以便迁移可以重复执行并安全回滚。
18. 作为运维人员，我希望 release rollback 与 data rollback 分开验证，以便旧制品回退成功不会被误解为外部世界数据也已恢复。
19. 作为维护者，我希望默认不改变路径、key、wire id、格式、默认启用规则、pack manifest 或 sandbox policy，以便普通重构不会附带不可见的数据契约变化。
20. 作为性能负责人，我希望性能采样是独立 P0 工作而不是 W0 manifest 的一部分，以便基线记录环境、负载、预热、重复次数和统计口径。
21. 作为维护者，我希望性能发布阈值在取得基线后、P4 前由维护者基于数据确认，以便实现者不会自行编造门槛或把性能工作省略为不阻塞。
22. 作为脚本作者，我希望离线 validator 或 migration report 只能显式运行、默认只读，并且不是硬 release gate，以便普通运行时错误保持简洁且不产生第二套 Script API。
23. 作为发布负责人，我希望 validator 不替代公开接口迁移表、必要的数据迁移与回滚 fixture 或 contract diff，以便辅助工具不会被当成验收捷径。
24. 作为 Fabric 用户，我希望 1.2.0 的 Fabric WORLD pack 保持当前行为并公开已知生命周期差异，以便能力差异可见而不被 experimental 身份掩盖。
25. 作为构建负责人，我希望 Fabric `common-api-processor` 在 1.2.0 延期，并以非 processor 的 contract、event 和 declaration 覆盖 gate 补足，以便延期不会变成未说明的空白。
26. 作为平台负责人，我希望 Fabric 已声明能力仍必须通过构建、制品、contract 和 smoke，且延期不被描述为 NeoForge processor 等价，以便平台验证保持诚实。
27. 作为维护者，我希望新增事件、新增 Adapter、新增扩展点和新增版本四类试做都是 release gate，以便每类真实维护任务都能追到 owner、事实源、依赖方向、受影响节点和测试。
28. 作为发布负责人，我希望 `1.2.0` 交接同时包含五节点报告、capability matrix、公开契约报告、语言语料、runtime smoke、数据迁移与回滚、维护者试做和文档一致性检查，以便发布不依赖缺失证据的推断。

## Implementation Decisions

- P0-P4 与 W0-W10 属于同一次 `1.2.0` clean cutover。内部阶段只用于排序和验收，不分别发布，也不授权今后的任意 breaking。
- 本轮不立即修改版本号，不执行格式迁移，不保留长期 compatibility shim、deprecated wrapper 或双运行时路径。公开 breaking 只通过一次 cutover 和迁移表交付。
- P0 固定五节点、功能覆盖账本、公开契约、golden、制品、Graal/运行环境、数据 fixture 和性能输入；普通测试不得写 golden、manifest、Probe 基线或其他规范产物。
- P1 以 `NekoRuntimeRoot` 为唯一 runtime owner，验证 startup、CLIENT、afterInit、reload、close、错误阶段和资源释放；失败候选不得替换旧 active runtime/state，也不得双注册平台资源。
- P2 垂直迁移 Plugin Runtime、Script Preparation、Module Resolution/Cache、Script Execution Environment、Managed Surface 和 Registry Runtime；调用者只跨小 Interface，不保留第二语义 pipeline 或第二规范源。
- P3 逐域迁移并验证事件、recipe、client/UI/render、network/PData、command、diagnostics 和 pack trust。每个 coverage ledger 行必须有 capability matrix、contract fixture、loader smoke、artifact/source trace 和外部 addon fixture。
- P3 不重新采用历史方案 C。历史候选只保留背景比较价值，物理 loader/version Module 或 API artifact 只有在真实发布、依赖或测试收益出现时另行评估。
- P4 删除无调用者的重复路径，完成迁移表、支持矩阵、数据回滚 fixture、全节点 release report 和维护者四类试做；兼容桥只有在替代证据齐全且旧路径无消费者时才能删除。
- golden 只冻结有意承诺的公开 contract，例如 managed surface、legacy preview、Plugin Point/Hook/Handle、registry declaration、packet、diagnostic 和 capability matrix；不得冻结 private helper、目录布局或对象身份。
- golden 更新必须显式生成，并同时提供旧新 diff、更新原因、受影响功能、迁移影响和维护者审阅记录。普通测试只做读取和比较，不自动回写基线。
- capability matrix 只允许 `supported`、`partial`、`unavailable`。`未验证` 与 `deferred` 是独立维度；缺少测试或尚无执行结果只能标为未验证并阻塞对应验收，不能直接改写为 `unavailable`。
- `26.1.2` primary 的回归阻塞 release；`26.2.0` secondary 必须通过构建、制品、契约和已声明能力验证；`1.21.1` 与两个 Fabric experimental 节点至少通过可重复构建、制品和已声明能力 smoke。
- 普通重构默认不改变 config、world、pdata、脚本与 pack 路径、格式、key、wire id、默认启用规则、pack manifest 或 sandbox policy。任何例外都进入迁移表并附旧数据 fixture。
- 只有格式确有必要变化时才引入专用 migration，并覆盖备份或原子替换、schema/version、旧 fixture 回读、幂等验证、失败恢复和验证完成前保留原始数据。不得引入通用 migration framework。
- release rollback 与 data rollback 分开：旧制品可以回退，但受保护的 config、world、pdata、脚本与 pack、trust-store、用户编辑 workspace/declaration 和历史日志在验证完成前不得被覆盖或删除。
- reload 失败只保证 NekoJS 所拥有的 runtime 资源回退，不承诺撤销脚本通过 Java、网络、世界或其他外部对象造成的副作用。该边界必须在错误、smoke 和迁移说明中可见。
- 性能采样是独立 P0 工作，记录环境、负载、预热、重复次数和统计口径；发布阻断阈值在取得基线后、P4 前由维护者基于数据确认，未确认的阈值不得由实现者自行设定。
- 离线 validator 或 migration report 可选、默认只读，不是 1.2.0 硬 release gate；它不得进入普通 runtime 错误路径，也不得成为新的 Script API 事实源或替代迁移表、数据 fixture、contract diff。
- 普通 runtime 错误保持普通形式，不携带 validator 或迁移修复提示；辅助检查只能通过显式运行的离线工具完成。
- Fabric WORLD pack 在 1.2.0 保持当前行为并公开已知生命周期差异，不要求本次达到完整 parity。能力表按实际行为和证据记录 `partial` 或 `unavailable`，延期是工作状态而非第四种 capability 值。
- Fabric `common-api-processor` 接入在 1.2.0 延期，不得在 Fabric source root 迁移中顺手启用。必须提供非 processor 的 contract、event 和 declaration 覆盖 gate，逐项写明 owner、输入、输出和失败诊断。
- Fabric processor 延期不免除已声明能力的构建、制品、contract 和 smoke 验证，也不得宣称具有 NeoForge processor 等价性。若规范 `ALL` 与实际能力不符，必须显式记录差异并审阅。
- 四类维护者试做是 release gate：新增事件、新增 Adapter、新增扩展点、新增版本。每类都必须能从入口追到 owner、事实源、依赖方向、受影响节点和测试；注册类型/Builder 与平台能力必须在 Adapter 试做中区分。
- 1.2.0 发布前必须同时存在五节点构建/检查/制品报告、capability matrix、managed/legacy/plugin/registry contract 报告、语言 corpus 与 source-map 报告、runtime smoke、数据迁移与回滚 fixture、维护者试做记录、脚本与插件迁移文档以及 README/wiki/ADR 一致性检查。
- 本规格只形成执行视图，不批准删除具体源码、执行格式迁移、运行发布或宣称任何验证已通过。

## Testing Decisions

- 测试优先穿过最高调用者 Seam。运行时所有权在 `NekoRuntimeRoot` 生命周期 Interface 上验证，公开契约在 `NormativeApiContract` 与 Managed Surface Interface 上验证，插件在 Plugin Runtime/Handle Interface 上验证，registry 在 Registry Runtime/Adapter 与声明 Interface 上验证。
- 成功断言必须观察外部行为：startup、CLIENT 和 afterInit 只经一个 owner 发生；候选完整通过后才切换；旧 generation 停止接收新回调；资源按所有权顺序释放；契约和声明保持确定一致。
- 失败断言必须覆盖候选准备、执行、绑定或域 preflight 失败：候选资源全部关闭，旧 active runtime/state 继续可用，不出现双 callback、双注册、半提交或错误阶段丢失。
- 取消与关闭断言必须覆盖 reload 重入、close 优先于尚未开始的 reload、candidate watchdog 终止后丢弃候选、active watchdog 终止后等待显式恢复；不得自动创建第二个 active runtime。
- 清理断言必须覆盖旧 listener、timer、binding、Context、module cache 和域计划按 generation 失效，同时确认进程级 Plugin Runtime、受保护持久化数据和用户编辑声明不被误清。
- `ScriptReloadRegressionTest` 是事务候选、statement-limit 终止、timer callback 和 teardown 清理的 prior art；`ReloadMemoryStabilityTest` 是连续 reload 资源稳定性的 prior art；`PlatformConcurrencyTest` 是单次初始化和竞态语义的 prior art。
- `EventBusConcurrentStressTest` 是并发 mutation/post 后回到空状态的 prior art；相关验证应观察事件可见性、取消、优先级和清理结果，不冻结私有调度器实现。
- `ApiManifestGoldenTest`、`EventApiSurfaceGoldenTest`、`ProbeOutputCompatibilityTest` 与 `NekoCompilerGoldenTest` 是显式 golden 与确定性派生的 prior art。成功断言是基线变化只在显式 regenerate 后发生；失败断言是普通测试写入基线、派生输出反向决定运行时行为或无意 surface drift。
- `PluginHookPairingTest`、`NekoPluginBootstrapV2Test` 和 `NekoPluginBootstrapHookDeliveryTest` 是插件发现、Hook 配对、依赖、freeze 与 Handle 交付的 prior art；测试应穿过公开契约验证结果，不依赖私有类分布。
- `RegistryBridgeIntegrationTest`、`DynamicRegistryBuilderTest` 和 `DynamicRegistrationBookkeepingTest` 是 registry 桥接、Builder 形状、claim/stale 与错误语义的 prior art；新验证需补充 transaction、ID/sync、declaration parity 和失败回滚。
- 语言验证沿用 compiler golden、TypeScript erasure corpus 与 Python golden 的外部行为类型，覆盖 JS/ESM/CJS、TS/JSX/TSX、Python、source map、错误位置、module mode 和 Graal 执行，不冻结 Eraser 私有布局。
- `SandboxConfigLoaderTest`、`SandboxPolicyTest`、`ScriptPackRegistryTest`、`PackSyncTrustStoreTest`、`ScriptPackRegistryServerCacheTest`、`PersistentDataJSTest` 与 `PDataSyncAcceptTest` 是配置、路径、trust、pack 和 pdata 保护的 prior art；迁移测试必须使用旧 fixture 回读并断言失败恢复与原始数据保留。
- `checkCommonIsolation` 对应 common 的 MC/loader 隔离 Seam；成功断言是共享引擎不引入平台类型且不误禁 GraalJS，失败断言是平台类型反向进入 common。
- 五节点构建/制品、source trace、runtime smoke 和外部 addon fixture 属于发布 gate，不是单个单元测试可替代的证据。每个节点必须输出 discovered/skip/count、制品、声明能力和失败日志。
- 四类维护者试做不引入新测试框架，直接执行新增事件、Adapter、扩展点和版本的真实任务；入口、owner、事实源、依赖方向、受影响节点或测试任一项需要跨处猜测即视为失败。
- 当前没有把全部 P0-P4 证据执行完成；本规格中的通过条件都是待实施验证的契约，本轮未运行构建、测试或运行时迁移。

## Out of Scope

**本轮不实施或验证（不构成未来永久禁止）**

- 本轮不修改源码、运行构建或测试、更新 golden、生成迁移产物、执行数据迁移或发布 `1.2.0`。
- 本轮不现在修改版本号，或把 P0-P4 拆成多次公开 breaking 发布。
- 本轮不删除具体源码、退役节点、改变支持矩阵、重开 Stonecutter 终局或重新采用历史方案 C；这些必须在对应 Resolution、替代证据和 gate 下另行批准，P4 仍可按删除条件清理无调用者的重复路径和兼容桥。

**本规格不承诺或明确排除的目标**

- 保留长期 compatibility shim、deprecated wrapper、双运行时路径或第二套语义 pipeline。
- 新增 Gradle project、API artifact、通用 migration framework、万能 Event Module 或新的全仓测试框架。
- 把可选离线 validator 变成硬 release gate、普通 runtime 错误提示或第二套 Script API。
- 在取得性能基线前设定发布阻断阈值，或用“计划存在”代替性能、smoke、数据或维护者试做证据。
- 创建 GitHub issue 或以本规格代替源决策的 Resolution。

## Further Notes

- 本规格派生自 [怎样以可验证的阶段完成本次重构并作为新标准？](../decisions/07-validation-and-migration.md)。该票的 Resolution 是裁决权威；本文件只是执行视图，若两者冲突以源决策为准。
- 相关源决策包括 [版本与加载器差异如何组织，Stonecutter 何去何从？](../decisions/03-platform-build-strategy.md)、[脚本表面与插件作者模型如何只有一个事实源？](../decisions/04-public-contract-and-plugin-model.md)、[运行时所有权、reload 与数据保护的契约是什么？](../decisions/05-runtime-lifecycle-and-data.md)、[reload 候选环境、状态所有权与线程边界如何闭合？](../decisions/09-reload-candidate-state-and-thread-contract.md) 和 [跨 reload 的 global 共享状态如何参与候选事务？](../decisions/10-shared-global-candidate-writes.md)。
- 相关派生规格包括 [搬运功能事件面规格](./08-ported-features-event-surface.md)。实施顺序、物理落点、W8/W9 接线和删除条件见 [NekoJS 实施交接单](../implementation-handoff.md)，功能覆盖账本见 [NekoJS 目标架构与迁移建图](../proposal.md)。
- 测试、golden、CI 与可观测性证据见 [测试、文档、CI 与可观测性证据](../evidence/testing-and-docs.md)；平台差异见 [构建与平台证据](../evidence/build-and-platforms.md)。
- `Status: ready-for-agent` 只表示规格可交给代理继续处理，不构成源码、测试、构建、迁移、删除或发布授权。
- 所有验证均未因本规格生成而通过；本规格不把规划完成、静态清单或已关闭票当作 release evidence。
