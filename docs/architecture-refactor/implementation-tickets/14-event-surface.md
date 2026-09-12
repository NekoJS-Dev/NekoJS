# 14: 事件总线与 Script/Native/Probe 事件声明基础

**What to build:** 脚本作者通过既有 bus 注册、取消、排序和清理事件；ScriptEvents 的动态声明、NativeEvents 的 raw adapter 面和 ProbeEvents 的 Probe 扩展面进入各自正确 tier，并由同一 managed contract/catalog 派生 TS/Python declaration。平台 callback/mixin 仍在 Adapter，不创建万能 Event Module或重复事件。

**Blocked by:** [09: Managed Surface 单一规范源与声明/Probe 派生链](09-managed-surface.md)、[07: 同类型串行、close 优先与 watchdog 隔离恢复](07-runtime-threads.md)

**Status:** ready-for-agent

**Assignee:** unassigned

**Optional:** false

**Selected:** true

**Human input:** none

**Work items:**

- 冻结事件 bus 的注册、remove/dispatch、priority、cancel、side filter、线程/重入和 reload 后清理外部行为，复用 EventBusConcurrentStressTest 形状。
- 把共同事件名、payload 成员、side、dispatch、cancel 和 tier 纳入 managed contract/catalog；原生回调、mixin和平台时机留在 src/或 node Adapter。
- 为 ScriptEvents 建立动态事件组、注册冲突、side、reload 清理、runtime member 与 TS/Python declaration parity 的同一 catalog 派生路径。
- 将 NativeEvents 明确保留为 legacy/raw Adapter 观察面，记录 capability 与声明来源，不静默升级 managed。
- 将 ProbeEvents 限定为 Probe 扩展面，验证事件到真实 catalog/declaration golden，不变成通用运行时事件。
- 建立 EventApiSurfaceGoldenTest/NekoScriptCatalogEventsTest 级别的无重复 bus、独立 side entry 和能力矩阵 fixture。
- 收缩 gate：旧事件注册/声明旁路只有在替代 behavior、declaration、trace 与无调用者证据通过后移除；NativeEvents/ProbeEvents 的合法旧 tier 不因收缩被删除，公开功能不删除，清理不推迟 final release。

## Acceptance criteria

- [ ] 脚本注册、触发、取消、优先级和 remove 的外部行为在并发 mutation/post 后最终回到空状态且无重复 dispatch。
- [ ] server-only、client-only 和 mixed side 事件在 catalog/declaration 中保持独立条目，side filter 不产生重复声明。
- [ ] ScriptEvents 动态事件注册冲突、未知依赖/组、side错误和 reload 清理有可诊断失败与 fixture。
- [ ] NativeEvents 保留 raw/legacy tier 与 capability 说明，不被 catalog 收录动作升级为 managed stable。
- [ ] ProbeEvents 输出真实 Probe/catalog/declaration golden，不创建通用运行时事件或第二事件 bus。
- [ ] 每条 bus 只出现一次；已有事件域不被复制为 ScriptEvents/NativeEvents/ProbeEvents 新事件。
- [ ] TS/Python declaration 与 runtime member、payload 形状、side 和 capability 一致。
- [ ] 平台 callback/mixin/transport 时机留在 loader/version Adapter，common 不引入 Minecraft/loader 类型。
- [ ] reload 失败或取消后 listener 不双注册、不泄漏到旧 generation，active callback 可见性符合 runtime 语义。
- [ ] 不新增万能 Event Module、无生命周期 Point或第二注册路径；旧事件旁路仅在替代 behavior、declaration、trace 通过且无调用者后移除，合法 legacy/raw tier 与公开功能不删除，清理不推迟 final release。
- [ ] 随实现交付 Script/Native/Probe 事件的最小可运行示例与必要迁移材料；示例只使用已通过 gate 的 tier、side 和 capability，不把 raw/legacy 示例标成 managed stable。

## Sources

- [NekoJS 实现票据拆分草案](../implementation-ticket-breakdown.md)
- [公开契约与插件模型规格](../specs/04-public-contract-and-plugin-model.md)
- [NekoJS 验证与迁移规格](../specs/07-validation-and-migration.md)
- [NekoJS 搬运功能事件面规格](../specs/08-ported-features-event-surface.md)
- [NekoJS 实施交接单](../implementation-handoff.md)
- [实现票据索引](README.md)

## Dependency rationale

- [09: Managed Surface 单一规范源与声明/Probe 派生链](09-managed-surface.md): 事件公开名、payload、side、tier 和 declaration 必须以 NormativeApiContract 为规范源并从 catalog 派生；先改 bus 会制造第二事件规范。
- [07: 同类型串行、close 优先与 watchdog 隔离恢复](07-runtime-threads.md): 公开事件的跨线程派发、重入与关闭验收需要最终 owner 队列和 watchdog 隔离结果。

## Scope and coordination

**Rationale:** 事件基础必须覆盖 bus 与三类容易混淆的事件声明 owner，但只建立公共路径；recipe、capability、goal、实体行为等已有域另有窄票补齐，避免“所有事件迁移”巨票。

**Coordination:**

- RUNTIME_ROOT/RELOAD_COMMIT: generation 切换、失败保留和 listener 清理的最终事务语义由 runtime 组提供；现有 runtime smoke 只能先作 characterization；candidate 隔离与 cleanup 的新状态验收等待 RELOAD_COMMIT。
- PLUGIN_ADDON: Probe/插件贡献发现和外部 addon fixture 由插件组承接，本票只定义事件/Probe 声明投影。
- RECIPE_DATA_SURFACE/GAMEPLAY_EVENT_SURFACE: 既有事件域在本基础 上补域内语义，不以文件冲突为由改成串行阻塞。
- BUILD_BASELINE: 事件 catalog/golden 以当前快照为 characterization 旧输入。

票据发布不代表已完成验收或本轮授权源码实施；完成条件与认领规则见本目录索引。
