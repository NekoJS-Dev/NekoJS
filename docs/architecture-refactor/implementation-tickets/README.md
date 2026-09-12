# NekoJS 实现票据索引

本目录是正式本地实现票据：**47 张必选票 + 1 张可选票，一票一文件**。首轮发布为 37 必选 + 1 可选；2026-09-09 根据用户“修下票”的授权补齐源码审计缺口并新增 Item/Block modification 专项票。2026-09-10 另按[专项产品决定](../editor-removal-and-error-ui.md)修订票据口径；本目录修订不开始源码重构，源码删除由专项工作流另行验证。


2026-09-12 用户批准 JSX UI 整合提案后发布 40–48 共 9 张 feature 票。新增票在 JSX feature 范围内必选，但不自动纳入既有 1.2.0/P4 发布范围；34–37 的阻塞关系不变。历史拆分和既有检查快照保留原样。

## 来源与权威关系

- [NekoJS 实现票据拆分草案](../implementation-ticket-breakdown.md) 保留为已确认的历史拆分输入；其中“草案、尚未发布”等字样描述当时状态，不代表本目录未发布。按技能要求，不修改父文档。
- [规格索引](../specs/README.md) 描述各域完整行为；源决策 Resolution 仍是架构裁决依据。票据把已确认规格转成可验证的工作，不创造另一套契约。
- [实施交接单](../implementation-handoff.md) 提供源码定位、迁移、验证与删除前提；[规划与实施验收清单](../planning-completion-checklist.md) 区分规划与实施证据；[领域术语表](../../../CONTEXT.md) 统一词义。
- 后续执行以每票的 What to build、Acceptance criteria、Blocked by 和状态字段为准。若源码新证据要求改变已定语义，先回到对应本地决策；不能用完成票据为由越过数据、功能或发布边界。
- 2026-09-10 的[编辑器移除与只读报错 UI 专项说明](../editor-removal-and-error-ui.md)是用户明确批准的窄范围产品决定：仅移除内置游戏内编辑器/workspace GUI 与编辑文件同步，保留外部 WorkspaceGenerator/Probe/types/jsconfig、用户数据和只读错误报告。该专项满足“公开功能删除须维护者确认”的确认要求，但不重开历史决策/spec，也不自动放行 03+ 或整个重构。

## 本轮修订边界

- 上一轮拆分及已关闭决策不重开；新增覆盖由本轮用户修票授权承接。新增票不等于原 spec 已逐项列出该域，继承的 runtime、数据保护与事件契约仍须遵守。若行为证据确需改变既定公开语义，先记录冲突并回到决策，而不是借修票扩大产品承诺。
- “结构重构”以明确 owner、依赖方向、单一事实源和平台 Adapter 边界为目标，不以搬完所有文件或增加项目数量为目标；目录/包归属规则覆盖全量源码，逐文件只记录例外，最终由 P4 核查。
- 实现票的 Work items 使用领域符号；物理落点以已批准的[实施交接单](../implementation-handoff.md)对应工作单为入口，不另建漂移的路径清单。
- 新增既有 Item/Block modification 专项；补齐 Block/Item/Level/Player/Command 等事件族、Lang 与插件生成 Hook；修正数据保护、诊断投影、动态注册提交依赖。
- API 相关票随实现交付可运行的最小示例和迁移材料；最终交接只汇总，不把文档首稿和大范围清理推迟到发布。
- [PR 37 文档参考复核](../evidence/pr37-ticket-reference-review.md)区分历史 skeleton、当前实现和目标契约；吸收扩展点与连带注册的有效建议，不复制另一套框架、不误禁启动期高级注册能力。

## 认领、前沿与完成

1. **先确认执行授权。** 本轮只发布票据；`ready-for-agent` 表示工作已说明充分，不等于已经开工，也不等于没有 blocker。
2. **查询票内 metadata。** 可以认领的 agent 票必须同时满足：Status 为 `ready-for-agent`、Assignee 为 `unassigned`、Selected 为 `true`，且 Blocked by 中每个先决票均为 `closed`。编号是稳定身份，不代表执行顺序；补充票 39 可作为旧编号票的前置。必须按 Blocked by 查询依赖图，不从小到大机械执行。
3. **先认领再工作。** 获准执行后先将 Assignee 写为当前执行者、Status 改为 `in-progress`。状态只维护在单票内，不另建镜像进度表；协调事项与代码写集冲突不自动变成语义依赖。
4. **人工门禁不能代答。** `ready-for-human` 票也要先完成 blockers；agent 可准备证据，但不得代替维护者填写政策确认或试做认可。
5. **可选票默认不执行。** Optional 为 `true` 且 Selected 为 `false` 的离线报告只是在本次发布中占有一张票；之后明确选用才修改 Selected。任何必选票都不依赖它。
6. **以证据关闭。** 逐项满足验收条件、记录输入与复现方式、相关测试/制品/迁移和删除条件的结果及限制后，才勾选验收项并将 Status 改为 `closed`。人工票还需记录维护者真实结论。不能把发票、代码写完、缺测或 deferred 直接当作验收通过。
7. **不擅自绕过失败。** 验收失败时保留未完成状态、失败证据和负责人；若需要外部输入可记 `blocked`，条件解除后恢复相应待认领或进行中状态。不得通过取消先决票、改 capability 或勾选未运行测试来放行下游。

所有票的源码更改与验证都须在已授权范围内进行；普通测试不更新 golden。临时内部迁移遵循已定 expand–migrate–contract 和删除条件，最终 1.2.0 不留下第二 runtime、第二事实源、长期 shim 或旧新双路径。发布交接票只准备并验证本地候选制品，不自动执行远程上传或公告。

## 全部实现票

下面是导航，不是第二份可变状态表。当前状态、认领者和实际前沿必须读取各票 metadata。

| 票据 | 参与方式 |
|---|---|
| [01: P0 五节点构建与契约基线](01-build-baseline.md) | 必选；agent 可执行 |
| [02: P0 独立性能基线](02-perf-baseline.md) | 必选；agent 可执行 |
| [03: 持久化与用户编辑数据保护基线：默认不改、可回滚才迁移](03-data-protection.md) | 必选；agent 可执行 |
| [04: P4 前性能发布政策确认](04-perf-release-policy.md) | 必选；维护者给出真实结论 |
| [05: 单 owner 预整理：闭合两个 loader 的运行时生命周期入口](05-runtime-root.md) | 必选；agent 可执行 |
| [06: 候选环境、阶段结果与 owner-thread commit 点](06-reload-commit.md) | 必选；agent 可执行 |
| [07: 同类型串行、close 优先与 watchdog 隔离恢复](07-runtime-threads.md) | 必选；agent 可执行 |
| [08: 真实外部 PluginAddon 从 discovery 到贡献消费与 reload 存活](08-plugin-addon.md) | 必选；agent 可执行 |
| [09: Managed Surface 单一规范源与声明/Probe 派生链](09-managed-surface.md) | 必选；agent 可执行 |
| [10: 按类型 global、显式 shared 与候选顶层写集联合提交](10-global-state.md) | 必选；agent 可执行 |
| [11: JS/CJS/ESM 模块身份、缓存与生命周期基础路径](11-language-pipeline.md) | 必选；agent 可执行 |
| [12: TS/JSX/TSX 编译、source map 与执行行为路径](12-language-ts.md) | 必选；agent 可执行 |
| [13: Python 转译、模块行为与诊断路径](13-language-py.md) | 必选；agent 可执行 |
| [14: 事件总线与 Script/Native/Probe 事件声明基础](14-event-surface.md) | 必选；agent 可执行 |
| [15: 启动期注册、typed Builder 与连带注册垂直收口](15-registry-startup.md) | 必选；agent 可执行 |
| [16: Dynamic Registry inert 定义计划与 typed Builder](16-registry-dynamic-local.md) | 必选；agent 可执行 |
| [17: 网络注册一次、wire 不变与脚本自定义通道 owner 调度](17-network-sync.md) | 必选；agent 可执行 |
| [18: PData 与 ClientData 数据同步路径保护和 generation 边界](18-data-sync.md) | 必选；agent 可执行 |
| [19: 脚本包分发 trust 决策、远端包激活与 Fabric WORLD 现状](19-pack-trust.md) | 必选；agent 可执行 |
| [20: 管理命令权限、生命周期入口与阶段诊断结果](20-runtime-commands.md) | 必选；agent 可执行 |
| [21: Dynamic Registry 多人 prepare/ack/commit 门禁](21-registry-dynamic-sync.md) | 必选；agent 可执行 |
| [22: Villager Trades 声明事件与稳定查询](22-villager-trades.md) | 必选；agent 可执行 |
| [23: Recipe/数据生成/loot/tags/recipe viewer 既有事件域路径](23-recipe-data-surface.md) | 必选；agent 可执行 |
| [24: Block/Item/Level/Player/Command/Capability/goal/实体行为既有事件面覆盖路径](24-gameplay-event-surface.md) | 必选；agent 可执行 |
| [25: DataMap 与 EntitySelectors 查询 binding/Adapter/declaration 路径](25-query-tools.md) | 必选；agent 可执行 |
| [26: CLIENT 输入与 HUD callback 生命周期](26-client-input-hud.md) | 必选；agent 可执行 |
| [27: CLIENT GUI 与 render Adapter 资源呈现清理](27-client-gui-render.md) | 必选；agent 可执行 |
| [28: PostEffects 声明事件与运行 binding 分离](28-post-effects.md) | 必选；agent 可执行 |
| [29: Assets/Lang 资源生成与回读收口](29-assets.md) | 必选；agent 可执行 |
| [30: 错误诊断、telemetry、workspace 与用户报告链路](30-diagnostics.md) | 必选；agent 可执行 |
| [31: Fabric raw loader 源根显式所有权迁移](31-build-fabric-raw-root.md) | 必选；agent 可执行 |
| [32: Fabric 五层源唯一性与 bridge 删除条件](32-build-fabric-trace-cutover.md) | 必选；agent 可执行 |
| [33: CI 用途子集与 Fabric processor 延期替代 gate](33-build-ci-processor-gate.md) | 必选；agent 可执行 |
| [34: P4 五节点整体验证与能力矩阵收口](34-release-p4-validation.md) | 必选；agent 可执行 |
| [35: P4 性能复测与政策对照](35-release-perf-compare.md) | 必选；agent 可执行 |
| [36: P4 维护者与脚本作者真实试做](36-release-maintainer-trials.md) | 必选；维护者给出真实结论 |
| [37: 1.2.0 clean cutover 与发布交接](37-release-1-2-0-handoff.md) | 必选；agent 可执行 |
| [38: 离线 validator / migration report（可选）](38-offline-migration-report.md) | 可选；默认未选用 |
| [39: Runtime Item/Block modification 候选计划与 snapshot ownership](39-item-block-modification.md) | 必选；agent 可执行 |
| [40: JSX UI common core、公开契约与 Fake Host Proof](40-jsx-ui-common-core.md) | JSX feature 必选；agent 可执行 |
| [41: NeoForge 26.2 JSX Screen、输入、焦点与滚动 Adapter](41-jsx-ui-neoforge-screen-adapter.md) | JSX feature 必选；agent 可执行 |
| [42: JSX UI CLIENT generation、reload、诊断与 cleanup 接线](42-jsx-ui-generation-reload-cleanup.md) | JSX feature 必选；agent 可执行 |
| [43: 六档 Viewport Profile 与响应式布局系统](43-jsx-ui-viewport-profiles.md) | JSX feature 必选；agent 可执行 |
| [44: 文本测量、视觉样式、图片与受控资源解析](44-jsx-ui-text-visual-assets.md) | JSX feature 必选；agent 可执行 |
| [45: UI Inspector、布局测量与截图差异基线](45-jsx-ui-inspector.md) | JSX feature 必选；agent 可执行 |
| [46: AI-assisted 网页转换映射与输入契约](46-jsx-ui-web-conversion-contract.md) | JSX feature 必选；agent 可执行 |
| [47: AI UI Authoring Contract 与转换 Cookbook](47-jsx-ui-ai-authoring-docs.md) | JSX feature 必选；agent 可执行 |
| [48: JSX UI feature end-to-end proof 与证据包](48-jsx-ui-end-to-end-proof.md) | JSX feature 必选；agent 可执行 |

## 初始前沿与并行安排

发布时所有验收项均未勾选、所有票均未认领。初始 agent 前沿为 [01: P0 五节点构建与契约基线](01-build-baseline.md) 和 [02: P0 独立性能基线](02-perf-baseline.md)。两者没有语义依赖，但必须固定共同的重构前 revision、各自隔离 checkout/run 与环境差异；构建环境修复不得混入性能采样。共享 CPU、磁盘或构建锁时需错开采样，避免污染性能基线。

性能政策票在独立性能基线后、P4 前由维护者确认；维护者与脚本作者代表性试做也需要真实人工参与。其余路径只等待各自 Blocked by，Fabric 构建、runtime、语言和功能域可在真实输入就绪后并行。共同文件必须分配不重叠写集并复验，不能仅因编号较晚就等待无关功能。

## Spec 到实现票的覆盖

以下为 Sources 派生导航，不替代源决策或各票验收；修票时同步检查，不能用“有链接”证明源码全覆盖。

| 来源 spec | 实现或验收票 |
|---|---|
| [PR 37 维护体验回归约束规格](../specs/00-pr37-maintainer-research.md) | [01: P0 五节点构建与契约基线](01-build-baseline.md)、[08: 真实外部 PluginAddon 从 discovery 到贡献消费与 reload 存活](08-plugin-addon.md)、[34: P4 五节点整体验证与能力矩阵收口](34-release-p4-validation.md)、[36: P4 维护者与脚本作者真实试做](36-release-maintainer-trials.md)、[37: 1.2.0 clean cutover 与发布交接](37-release-1-2-0-handoff.md) |
| [维护者模块设计与运行时所有权规格](../specs/01-maintainer-module-design.md) | [01: P0 五节点构建与契约基线](01-build-baseline.md)、[02: P0 独立性能基线](02-perf-baseline.md)、[04: P4 前性能发布政策确认](04-perf-release-policy.md)、[05: 单 owner 预整理：闭合两个 loader 的运行时生命周期入口](05-runtime-root.md)、[08: 真实外部 PluginAddon 从 discovery 到贡献消费与 reload 存活](08-plugin-addon.md)、[09: Managed Surface 单一规范源与声明/Probe 派生链](09-managed-surface.md)、[11: JS/CJS/ESM 模块身份、缓存与生命周期基础路径](11-language-pipeline.md)、[20: 管理命令权限、生命周期入口与阶段诊断结果](20-runtime-commands.md)、[31: Fabric raw loader 源根显式所有权迁移](31-build-fabric-raw-root.md)、[32: Fabric 五层源唯一性与 bridge 删除条件](32-build-fabric-trace-cutover.md)、[33: CI 用途子集与 Fabric processor 延期替代 gate](33-build-ci-processor-gate.md)、[34: P4 五节点整体验证与能力矩阵收口](34-release-p4-validation.md)、[35: P4 性能复测与政策对照](35-release-perf-compare.md)、[36: P4 维护者与脚本作者真实试做](36-release-maintainer-trials.md)、[37: 1.2.0 clean cutover 与发布交接](37-release-1-2-0-handoff.md)、[39: Runtime Item/Block modification 候选计划与 snapshot ownership](39-item-block-modification.md) |
| [版本与加载器支持矩阵规格](../specs/02-support-matrix.md) | [01: P0 五节点构建与契约基线](01-build-baseline.md)、[02: P0 独立性能基线](02-perf-baseline.md)、[04: P4 前性能发布政策确认](04-perf-release-policy.md)、[31: Fabric raw loader 源根显式所有权迁移](31-build-fabric-raw-root.md)、[32: Fabric 五层源唯一性与 bridge 删除条件](32-build-fabric-trace-cutover.md)、[33: CI 用途子集与 Fabric processor 延期替代 gate](33-build-ci-processor-gate.md)、[34: P4 五节点整体验证与能力矩阵收口](34-release-p4-validation.md)、[35: P4 性能复测与政策对照](35-release-perf-compare.md)、[36: P4 维护者与脚本作者真实试做](36-release-maintainer-trials.md)、[37: 1.2.0 clean cutover 与发布交接](37-release-1-2-0-handoff.md) |
| [平台构建与 Stonecutter 策略规格](../specs/03-platform-build-strategy.md) | [01: P0 五节点构建与契约基线](01-build-baseline.md)、[31: Fabric raw loader 源根显式所有权迁移](31-build-fabric-raw-root.md)、[32: Fabric 五层源唯一性与 bridge 删除条件](32-build-fabric-trace-cutover.md)、[33: CI 用途子集与 Fabric processor 延期替代 gate](33-build-ci-processor-gate.md)、[34: P4 五节点整体验证与能力矩阵收口](34-release-p4-validation.md)、[36: P4 维护者与脚本作者真实试做](36-release-maintainer-trials.md)、[37: 1.2.0 clean cutover 与发布交接](37-release-1-2-0-handoff.md) |
| [公开契约与插件模型规格](../specs/04-public-contract-and-plugin-model.md) | [08: 真实外部 PluginAddon 从 discovery 到贡献消费与 reload 存活](08-plugin-addon.md)、[09: Managed Surface 单一规范源与声明/Probe 派生链](09-managed-surface.md)、[14: 事件总线与 Script/Native/Probe 事件声明基础](14-event-surface.md)、[15: 启动期注册、typed Builder 与连带注册垂直收口](15-registry-startup.md)、[16: Dynamic Registry inert 定义计划与 typed Builder](16-registry-dynamic-local.md)、[21: Dynamic Registry 多人 prepare/ack/commit 门禁](21-registry-dynamic-sync.md)、[22: Villager Trades 声明事件与稳定查询](22-villager-trades.md)、[25: DataMap 与 EntitySelectors 查询 binding/Adapter/declaration 路径](25-query-tools.md)、[26: CLIENT 输入与 HUD callback 生命周期](26-client-input-hud.md)、[27: CLIENT GUI 与 render Adapter 资源呈现清理](27-client-gui-render.md)、[28: PostEffects 声明事件与运行 binding 分离](28-post-effects.md)、[29: Assets/Lang 资源生成与回读收口](29-assets.md)、[30: 错误诊断、telemetry、workspace 与用户报告链路](30-diagnostics.md)、[39: Runtime Item/Block modification 候选计划与 snapshot ownership](39-item-block-modification.md) |
| [运行时生命周期与数据保护规格](../specs/05-runtime-lifecycle-and-data.md) | [03: 持久化与用户编辑数据保护基线：默认不改、可回滚才迁移](03-data-protection.md)、[05: 单 owner 预整理：闭合两个 loader 的运行时生命周期入口](05-runtime-root.md)、[06: 候选环境、阶段结果与 owner-thread commit 点](06-reload-commit.md)、[07: 同类型串行、close 优先与 watchdog 隔离恢复](07-runtime-threads.md)、[10: 按类型 global、显式 shared 与候选顶层写集联合提交](10-global-state.md)、[17: 网络注册一次、wire 不变与脚本自定义通道 owner 调度](17-network-sync.md)、[18: PData 与 ClientData 数据同步路径保护和 generation 边界](18-data-sync.md)、[19: 脚本包分发 trust 决策、远端包激活与 Fabric WORLD 现状](19-pack-trust.md)、[20: 管理命令权限、生命周期入口与阶段诊断结果](20-runtime-commands.md)、[38: 离线 validator / migration report（可选）](38-offline-migration-report.md) |
| [语言模块管线规格](../specs/06-language-module-pipeline.md) | [11: JS/CJS/ESM 模块身份、缓存与生命周期基础路径](11-language-pipeline.md)、[12: TS/JSX/TSX 编译、source map 与执行行为路径](12-language-ts.md)、[13: Python 转译、模块行为与诊断路径](13-language-py.md)、[30: 错误诊断、telemetry、workspace 与用户报告链路](30-diagnostics.md) |
| [NekoJS 验证与迁移规格](../specs/07-validation-and-migration.md) | [01: P0 五节点构建与契约基线](01-build-baseline.md)、[02: P0 独立性能基线](02-perf-baseline.md)、[04: P4 前性能发布政策确认](04-perf-release-policy.md)、[09: Managed Surface 单一规范源与声明/Probe 派生链](09-managed-surface.md)、[11: JS/CJS/ESM 模块身份、缓存与生命周期基础路径](11-language-pipeline.md)、[12: TS/JSX/TSX 编译、source map 与执行行为路径](12-language-ts.md)、[13: Python 转译、模块行为与诊断路径](13-language-py.md)、[14: 事件总线与 Script/Native/Probe 事件声明基础](14-event-surface.md)、[15: 启动期注册、typed Builder 与连带注册垂直收口](15-registry-startup.md)、[16: Dynamic Registry inert 定义计划与 typed Builder](16-registry-dynamic-local.md)、[21: Dynamic Registry 多人 prepare/ack/commit 门禁](21-registry-dynamic-sync.md)、[22: Villager Trades 声明事件与稳定查询](22-villager-trades.md)、[23: Recipe/数据生成/loot/tags/recipe viewer 既有事件域路径](23-recipe-data-surface.md)、[24: Block/Item/Level/Player/Command/Capability/goal/实体行为既有事件面覆盖路径](24-gameplay-event-surface.md)、[25: DataMap 与 EntitySelectors 查询 binding/Adapter/declaration 路径](25-query-tools.md)、[26: CLIENT 输入与 HUD callback 生命周期](26-client-input-hud.md)、[27: CLIENT GUI 与 render Adapter 资源呈现清理](27-client-gui-render.md)、[28: PostEffects 声明事件与运行 binding 分离](28-post-effects.md)、[29: Assets/Lang 资源生成与回读收口](29-assets.md)、[30: 错误诊断、telemetry、workspace 与用户报告链路](30-diagnostics.md)、[31: Fabric raw loader 源根显式所有权迁移](31-build-fabric-raw-root.md)、[32: Fabric 五层源唯一性与 bridge 删除条件](32-build-fabric-trace-cutover.md)、[33: CI 用途子集与 Fabric processor 延期替代 gate](33-build-ci-processor-gate.md)、[34: P4 五节点整体验证与能力矩阵收口](34-release-p4-validation.md)、[35: P4 性能复测与政策对照](35-release-perf-compare.md)、[36: P4 维护者与脚本作者真实试做](36-release-maintainer-trials.md)、[37: 1.2.0 clean cutover 与发布交接](37-release-1-2-0-handoff.md)、[38: 离线 validator / migration report（可选）](38-offline-migration-report.md) |
| [NekoJS 搬运功能事件面规格](../specs/08-ported-features-event-surface.md) | [14: 事件总线与 Script/Native/Probe 事件声明基础](14-event-surface.md)、[15: 启动期注册、typed Builder 与连带注册垂直收口](15-registry-startup.md)、[16: Dynamic Registry inert 定义计划与 typed Builder](16-registry-dynamic-local.md)、[21: Dynamic Registry 多人 prepare/ack/commit 门禁](21-registry-dynamic-sync.md)、[22: Villager Trades 声明事件与稳定查询](22-villager-trades.md)、[23: Recipe/数据生成/loot/tags/recipe viewer 既有事件域路径](23-recipe-data-surface.md)、[24: Block/Item/Level/Player/Command/Capability/goal/实体行为既有事件面覆盖路径](24-gameplay-event-surface.md)、[25: DataMap 与 EntitySelectors 查询 binding/Adapter/declaration 路径](25-query-tools.md)、[26: CLIENT 输入与 HUD callback 生命周期](26-client-input-hud.md)、[27: CLIENT GUI 与 render Adapter 资源呈现清理](27-client-gui-render.md)、[28: PostEffects 声明事件与运行 binding 分离](28-post-effects.md)、[29: Assets/Lang 资源生成与回读收口](29-assets.md)、[39: Runtime Item/Block modification 候选计划与 snapshot ownership](39-item-block-modification.md) |
| [reload 候选状态与线程契约规格](../specs/09-reload-candidate-state-and-thread-contract.md) | [05: 单 owner 预整理：闭合两个 loader 的运行时生命周期入口](05-runtime-root.md)、[06: 候选环境、阶段结果与 owner-thread commit 点](06-reload-commit.md)、[07: 同类型串行、close 优先与 watchdog 隔离恢复](07-runtime-threads.md)、[08: 真实外部 PluginAddon 从 discovery 到贡献消费与 reload 存活](08-plugin-addon.md)、[10: 按类型 global、显式 shared 与候选顶层写集联合提交](10-global-state.md)、[16: Dynamic Registry inert 定义计划与 typed Builder](16-registry-dynamic-local.md)、[17: 网络注册一次、wire 不变与脚本自定义通道 owner 调度](17-network-sync.md)、[18: PData 与 ClientData 数据同步路径保护和 generation 边界](18-data-sync.md)、[20: 管理命令权限、生命周期入口与阶段诊断结果](20-runtime-commands.md)、[21: Dynamic Registry 多人 prepare/ack/commit 门禁](21-registry-dynamic-sync.md)、[22: Villager Trades 声明事件与稳定查询](22-villager-trades.md)、[26: CLIENT 输入与 HUD callback 生命周期](26-client-input-hud.md)、[27: CLIENT GUI 与 render Adapter 资源呈现清理](27-client-gui-render.md)、[28: PostEffects 声明事件与运行 binding 分离](28-post-effects.md)、[39: Runtime Item/Block modification 候选计划与 snapshot ownership](39-item-block-modification.md) |
| [global 共享状态与候选写入规格](../specs/10-shared-global-candidate-writes.md) | [10: 按类型 global、显式 shared 与候选顶层写集联合提交](10-global-state.md)、[21: Dynamic Registry 多人 prepare/ack/commit 门禁](21-registry-dynamic-sync.md)、[39: Runtime Item/Block modification 候选计划与 snapshot ownership](39-item-block-modification.md) |

## 功能覆盖账本到实现票的覆盖

对应 [原功能覆盖账本](../proposal.md#25-现有功能覆盖账本迁移前必须闭合) 的全部 23 行；此处只索引负责票，不复制域契约或宣称已有实现通过。

| 功能域 | 负责闭合的票 |
|---|---|
| 启动、runtime 生命周期与 reload | [05: 单 owner 预整理：闭合两个 loader 的运行时生命周期入口](05-runtime-root.md)、[06: 候选环境、阶段结果与 owner-thread commit 点](06-reload-commit.md)、[07: 同类型串行、close 优先与 watchdog 隔离恢复](07-runtime-threads.md) |
| 插件发现、Point、Handle 与 builtin 清单 | [08: 真实外部 PluginAddon 从 discovery 到贡献消费与 reload 存活](08-plugin-addon.md) |
| 编译、模块解析与语言路径 | [11: JS/CJS/ESM 模块身份、缓存与生命周期基础路径](11-language-pipeline.md)、[12: TS/JSX/TSX 编译、source map 与执行行为路径](12-language-ts.md)、[13: Python 转译、模块行为与诊断路径](13-language-py.md) |
| 脚本执行、bindings 与高级 Java access | [06: 候选环境、阶段结果与 owner-thread commit 点](06-reload-commit.md)、[07: 同类型串行、close 优先与 watchdog 隔离恢复](07-runtime-threads.md)、[09: Managed Surface 单一规范源与声明/Probe 派生链](09-managed-surface.md) |
| managed API、legacy surface、事件公开名与声明 | [09: Managed Surface 单一规范源与声明/Probe 派生链](09-managed-surface.md)、[14: 事件总线与 Script/Native/Probe 事件声明基础](14-event-surface.md) |
| 事件总线与 Block/Item/Level/Player/Command/Capability/Goal/Entity 事件族 | [14: 事件总线与 Script/Native/Probe 事件声明基础](14-event-surface.md)、[24: Block/Item/Level/Player/Command/Capability/goal/实体行为既有事件面覆盖路径](24-gameplay-event-surface.md) |
| 配方、ServerEvents.generateData 与 plugin generateData、loot、tags 与 recipe viewer | [23: Recipe/数据生成/loot/tags/recipe viewer 既有事件域路径](23-recipe-data-surface.md) |
| 既有 Item/Block 属性修改、snapshot 与候选恢复 | [39: Runtime Item/Block modification 候选计划与 snapshot ownership](39-item-block-modification.md) |
| Villager Trades | [22: Villager Trades 声明事件与稳定查询](22-villager-trades.md) |
| Dynamic Registry（服务器运行期） | [16: Dynamic Registry inert 定义计划与 typed Builder](16-registry-dynamic-local.md)、[21: Dynamic Registry 多人 prepare/ack/commit 门禁](21-registry-dynamic-sync.md) |
| 启动期 registry、Builder、声明注册与类型转换 | [15: 启动期注册、typed Builder 与连带注册垂直收口](15-registry-startup.md) |
| 客户端脚本、GUI、render、HUD 与 keybind | [26: CLIENT 输入与 HUD callback 生命周期](26-client-input-hud.md)、[27: CLIENT GUI 与 render Adapter 资源呈现清理](27-client-gui-render.md) |
| PostEffects | [28: PostEffects 声明事件与运行 binding 分离](28-post-effects.md) |
| 网络、脚本同步、ClientData、PData 与 pack sync | [17: 网络注册一次、wire 不变与脚本自定义通道 owner 调度](17-network-sync.md)、[18: PData 与 ClientData 数据同步路径保护和 generation 边界](18-data-sync.md)、[19: 脚本包分发 trust 决策、远端包激活与 Fabric WORLD 现状](19-pack-trust.md) |
| 管理命令、权限与脚本 CommandEvents | [20: 管理命令权限、生命周期入口与阶段诊断结果](20-runtime-commands.md)、[24: Block/Item/Level/Player/Command/Capability/goal/实体行为既有事件面覆盖路径](24-gameplay-event-surface.md) |
| sandbox、config、pack trust、cache 与持久化数据 | [06: 候选环境、阶段结果与 owner-thread commit 点](06-reload-commit.md)、[03: 持久化与用户编辑数据保护基线：默认不改、可回滚才迁移](03-data-protection.md)、[19: 脚本包分发 trust 决策、远端包激活与 Fabric WORLD 现状](19-pack-trust.md)、[11: JS/CJS/ESM 模块身份、缓存与生命周期基础路径](11-language-pipeline.md) |
| 错误、诊断、telemetry、workspace 与用户可见报告 | [30: 错误诊断、telemetry、workspace 与用户报告链路](30-diagnostics.md) |
| 数据映射查询 | [25: DataMap 与 EntitySelectors 查询 binding/Adapter/declaration 路径](25-query-tools.md) |
| 自定义事件声明 | [14: 事件总线与 Script/Native/Probe 事件声明基础](14-event-surface.md) |
| 原生事件桥与 Probe 事件 | [14: 事件总线与 Script/Native/Probe 事件声明基础](14-event-surface.md)、[09: Managed Surface 单一规范源与声明/Probe 派生链](09-managed-surface.md) |
| 实体选择器工具 | [25: DataMap 与 EntitySelectors 查询 binding/Adapter/declaration 路径](25-query-tools.md) |
| Assets、ClientEvents.lang 与 plugin generateAssets/generateLang | [29: Assets/Lang 资源生成与回读收口](29-assets.md) |
| 跨 reload 的 global 共享状态如何参与候选事务？ | [10: 按类型 global、显式 shared 与候选顶层写集联合提交](10-global-state.md) |
| 构建、版本兼容、资源、mixin 与五节点产物 | [01: P0 五节点构建与契约基线](01-build-baseline.md)、[31: Fabric raw loader 源根显式所有权迁移](31-build-fabric-raw-root.md)、[32: Fabric 五层源唯一性与 bridge 删除条件](32-build-fabric-trace-cutover.md)、[33: CI 用途子集与 Fabric processor 延期替代 gate](33-build-ci-processor-gate.md)、[34: P4 五节点整体验证与能力矩阵收口](34-release-p4-validation.md) |
| 最终物理结构与静态生命周期归属 | [05: 单 owner 预整理：闭合两个 loader 的运行时生命周期入口](05-runtime-root.md)、[34: P4 五节点整体验证与能力矩阵收口](34-release-p4-validation.md) |

## 首轮发布记录（历史）

首轮曾发布 38 张票（37 必选、1 可选）。该轮的 332 条验收项、95 条依赖和文件检查结果是历史快照；本次已补票并修订，当前目录以各票 metadata 和下方修订检查快照为准，不能继续用旧计数代表当前完整性。

## 修订检查快照（2026-09-09，历史）

- 当前 39 张票：38 必选、1 可选；37 张 `ready-for-agent`、2 张 `ready-for-human`；全部未认领，401 条验收项均未勾选。
- 107 条直接依赖；文档检查验证无环、无缺失 blocker，初始前沿仍为构建基线与独立性能基线；可选报告不阻塞必选票。
- Item/Block modification 必须进入 P4 整体验证、性能复测、真实试做及最终发布的传递依赖链；新增必选域时需检查这三条 gate，不只更新导航行。
- Sources/spec 导航、功能覆盖导航和票据本地链接均须同步验证；这只证明文档结构与分工，不证明源码功能已经完整实现。
- 没有勾选实施验收，没有修改源码、构建配置、用户数据或既有决策/spec/父级地图；未执行 Gradle、runtime smoke、性能、迁移或发布。

## 修订检查快照（2026-09-10）

- 重新校验当前 39 张票：38 必选、1 可选；37 张 `ready-for-agent`、2 张 `ready-for-human`；全部未认领，401 条验收项均未勾选；107 条直接依赖，初始前沿仍为构建基线与独立性能基线。
- 上述数量与勾选数来自当日重新执行文档检查脚本的实际解析结果。后续新增、拆分、合并或修订票据时必须重新校验并如实更新，不得沿用或强守旧计数。
- 2026-09-10 专项口径修订未改变票号、标题、Status、Assignee、Selected、Blocked by 或验收勾选；源码移除与验证结论由独立实现工作流另行记录。


## JSX UI 功能覆盖导航（2026-09-12）

- common core、状态、reconciler、声明与 fake host：40（消费 05/09/12）。
- 真实 Screen、输入与滚动：41；CLIENT generation/reload/诊断：42（消费 06/07/30）。
- 六档响应式布局：43；字体/视觉/资源：44（消费 29）。
- Inspector：45；AI 辅助网页转换契约：46；AI 编写文档与 Cookbook：47。
- 端到端、能力与性能证据：48；不是全节点或 1.2.0 发布 gate。

依据：[已批准整合提案](../jsx-ui-ticket-integration-proposal.md)、[JSX UI 规格](../../jsx-ui-spec.md)。提案中的 proposed 与未来时描述保留为批准前历史；当前票据状态和依赖只以正式单票为准，不以提案示意图调度。

### 2026-09-12 JSX UI 发布结构检查快照

- 正式票据 48 张：47 张必选、1 张可选；01–48 编号连续。
- 新增 40–48 共 9 张，包含 62 条未勾选验收条件；状态为 ready-for-agent，Assignee 为 unassigned，Selected 为 true。
- 全目录 Blocked by 目标存在，依赖图无环；新增票本地链接可解析，metadata 完整。
- 仅更新既有 09/26/27/29/30 的协调说明；历史拆分父文档及 34–37 发布票保持未变。
- 本快照只证明文档结构通过，不代表实现、构建、测试、客户端 smoke 或功能验收通过。
