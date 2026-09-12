# 30: 错误诊断、telemetry、workspace 与用户报告链路

**What to build:** 脚本语法/转换、模块 resolve/link、Graal 执行、reload/cancel、trust 和 watchdog 错误进入统一 frozen diagnostic record，保留阶段、owner、generation、ScriptType、模块身份和 source map 后的原始位置；本票拥有 record 生产者、公开字段契约、日志/报告/包投影与既有非 GUI 投影 contract fixture。本票的 workspace 指外部 IDE workspace/declaration/Probe 生成与定位；error dashboard 是只读报告，不是游戏内编辑器。最终 GUI/外部 workspace 打开接线由票 27 拥有，最终跨域真实集成由 P4 汇总；本票不等待 GUI 完成后才关闭。

**Blocked by:** [12: TS/JSX/TSX 编译、source map 与执行行为路径](12-language-ts.md)、[13: Python 转译、模块行为与诊断路径](13-language-py.md)、[07: 同类型串行、close 优先与 watchdog 隔离恢复](07-runtime-threads.md)、[19: 脚本包分发 trust 决策、远端包激活与 Fabric WORLD 现状](19-pack-trust.md)

**Status:** ready-for-agent

**Assignee:** unassigned

**Optional:** false

**Selected:** true

**Human input:** none

**Human input note:** agent 可以实现、测试并整理证据；涉及删除旧公开资源/诊断路径或最终 golden/发布确认的维护者 sign-off 不能由 agent 代答，未经 sign-off 不得删除旧路径或勾选对应删除验收项。

**Work items:**

- 统一 ErrorTracker/ScriptError/ErrorSummaryDTO/ScriptErrorReporter 的错误 ID、阶段、owner、source、generation、ScriptType、module identity、cause 和用户可见字段投影。
- 为 JS/CJS/ESM、TS/JSX/TSX、Python 的准备、resolve/link、缓存、执行和 reload/trust/watchdog 错误建立 source-map 与阶段矩阵。
- 让日志历史、dashboard packet、外部 IDE workspace source-path/action record seam、用户报告和 telemetry 消费同一 frozen diagnostic record；本票交付非 GUI record contract fixture，最终外部打开/只读屏幕接线由票 27 与 P4 验收。
- 保留 JavaClassLoadTelemetry 与 runaway watchdog 的既有可观察语义，补充隐私/体积边界和可重复采集口径。
- 验证 reload boundary：candidate 失败保留 active、generation/owner可读、旧错误历史不误归属新代际；外部副作用边界显式可见。
- 把可选 offline validator/migration report 保持为显式、默认只读的离线产物，不进入普通错误路径或 release 硬 gate。
- 收缩 gate：旧错误 tracker/report/dashboard旁路只有在替代 behavior、declaration/字段投影、trace 与无调用者证据通过后移除；公开诊断和外部 IDE workspace功能不删除，清理随本票完成而不是 final release 统一处理。“workspace 功能不删除”不保留内置编辑器、workspace GUI 或编辑文件同步。

## Acceptance criteria

- [ ] syntax/transform、resolve/link、cache、runtime、reload/cancel、trust 和 watchdog representative 错误均有正确阶段和 owner。
- [ ] JS、CJS、ESM、TS、JSX、TSX、Python 的错误位置能回映射到原始 source，并保留模块身份、行列和 cache/revision 信息。
- [ ] 同一错误在日志、ErrorSummaryDTO、packet/record projection、用户报告和现有外部 IDE workspace/declaration fixture 中呈现一致核心字段；本票不要求改完票 27 的最终屏幕，也不用私有 GUI 对象当契约。现状 `fullDetails` 快照可作为编辑器删除过渡证据，但不是本票目标 record。
- [ ] diagnostics owner 通过非 GUI seam 输出可解析、可定位的 source path/action record；generation、owner、ScriptType、source path、行列和 action payload 人类可读且可被 contract fixture 断言。实际外部 IDE 打开与只读报告 GUI 动作由票 27 消费该 record 实现，本票不提前实现或私有化 GUI 打开行为。
- [ ] 在 RELOAD_COMMIT 后的 candidate/active 状态中验证失败、取消、watchdog 终止与恢复：旧 active 错误历史不丢失，新候选错误不伪装成 active generation；旧 fixture只能作对照。
- [ ] telemetry/Java class-load/watchdog 记录可重复采集、可关闭或降级，并写明是否包含用户路径、脚本内容或环境信息。
- [ ] 普通 runtime 错误文本不包含 offline validator、migration report 或修复提示；辅助工具只能显式独立运行。
- [ ] 历史日志与用户在外部 IDE 编辑的 workspace config/declaration 在验证和迁移中不被覆盖或删除。
- [ ] 诊断 golden 只冻结公开字段和用户可见语义，不冻结 UI私有对象、布局或私有异常对象身份。
- [ ] 本票关闭范围是 frozen record、生产者和 contract fixture；票 27 负责最终只读 GUI 与外部 workspace 打开接线，票 34 负责跨域真实集成。旧投影/报告旁路仅在替代 behavior、declaration/字段投影、trace 通过且无调用者后移除，公开诊断和外部 IDE workspace功能不删除，清理不推迟 final release。

## Sources

- [NekoJS 实现票据拆分草案](../implementation-ticket-breakdown.md)
- [公开契约与插件模型规格](../specs/04-public-contract-and-plugin-model.md)
- [语言模块管线规格](../specs/06-language-module-pipeline.md)
- [NekoJS 验证与迁移规格](../specs/07-validation-and-migration.md)
- [NekoJS 实施交接单](../implementation-handoff.md)
- [内置游戏内编辑器移除与只读报错 UI 规划覆盖](../editor-removal-and-error-ui.md)
- [实现票据索引](README.md)

## Dependency rationale

- [12: TS/JSX/TSX 编译、source map 与执行行为路径](12-language-ts.md): 完整验收必须包含 TS/JSX/TSX 的原始 source-map、cache revision与阶段映射；仅用 LANGUAGE_PIPELINE 的 JS 基线不能代表全语言诊断。
- [13: Python 转译、模块行为与诊断路径](13-language-py.md): 完整验收必须包含 Python 的原始 source-map、cache revision与阶段映射；仅用 LANGUAGE_PIPELINE 的 JS 基线不能代表全语言诊断。
- [07: 同类型串行、close 优先与 watchdog 隔离恢复](07-runtime-threads.md): 诊断验收包括 active watchdog 隔离、取消及关闭状态，不能用旧调度状态代替新结果。
- [19: 脚本包分发 trust 决策、远端包激活与 Fabric WORLD 现状](19-pack-trust.md): trust representative 错误的阶段、owner、结果对象和用户可见字段必须基于最终 pack trust 链路，不能按旧 trust 行为提前关闭后回填。

## Scope and coordination

**Rationale:** 诊断是语言、事件、查询和 runtime 的共同用户可见结果；该票以统一上下文和投影一致性为边界，既不接管网络/client实现，也不把工具提示混入普通错误。

**Coordination:**

- RUNTIME_ROOT/RELOAD_COMMIT: active/candidate 保留、generation 切换和 watchdog 恢复语义由 runtime 组提供，本票负责诊断投影一致性。
- GLOBAL_STATE: 报告不得通过静态全局旁路读取错误状态。
- MANAGED_SURFACE: 诊断公开字段/错误码进入 managed contract，Probe/declaration只作投影。
- EVENT_SURFACE/RECIPE_DATA_SURFACE/GAMEPLAY_EVENT_SURFACE/QUERY_TOOLS: 各域错误必须回填阶段和 owner，不用私有异常绕过统一链路。
- CLIENT_GUI_RENDER/P4: 票 27 消费本票 record 完成最终只读 GUI；外部 IDE workspace 打开与错误报告职责分开，真实跨域集成由票 34 汇总，30 与 27 不形成互相等待。编辑器删除不是本票 record 设计的前置条件。
- PERF_BASELINE: telemetry采集成本有基线对照，不自行设定发布阻断阈值。

票据发布不代表已完成验收或本轮授权源码实施；完成条件与认领规则见本目录索引。

## JSX UI feature coordination（2026-09-12）

[42: JSX generation/reload/诊断接线](42-jsx-ui-generation-reload-cleanup.md) 消费本票统一 diagnostic record，补充 UI phase/root/generation 归因，不建立 UI 私有错误事实源。本票不反向依赖 JSX feature。
