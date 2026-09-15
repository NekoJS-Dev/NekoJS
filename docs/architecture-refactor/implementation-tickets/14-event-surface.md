# 14: 事件总线与 Script/Native/Probe 事件声明基础

**What to build:** 脚本作者通过既有 bus 注册、取消、排序和清理事件；ScriptEvents 的动态声明、NativeEvents 的 raw adapter 面和 ProbeEvents 的 Probe 扩展面进入各自正确 tier，并由同一 managed contract/catalog 派生 TS/Python declaration。平台 callback/mixin 仍在 Adapter，不创建万能 Event Module或重复事件。

**Blocked by:** [09: Managed Surface 单一规范源与声明/Probe 派生链](09-managed-surface.md)、[07: 同类型串行、close 优先与 watchdog 隔离恢复](07-runtime-threads.md)

**Status:** closed

**Assignee:** zcode-agent

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

- [x] 脚本注册、触发、取消、优先级和 remove 的外部行为在并发 mutation/post 后最终回到空状态且无重复 dispatch。【stress 补运行级"至少一次送达"下界（审查采纳）】
- [x] server-only、client-only 和 mixed side 事件在 catalog/declaration 中保持独立条目，side filter 不产生重复声明。
- [x] ScriptEvents 动态事件注册冲突、未知依赖/组、side错误和 reload 清理有可诊断失败与 fixture。
- [x] NativeEvents 保留 raw/legacy tier 与 capability 说明，不被 catalog 收录动作升级为 managed stable。
- [x] ProbeEvents 输出真实 Probe/catalog/declaration golden，不创建通用运行时事件或第二事件 bus。【唯一 golden 变更 = 新增 probe-events.expected.d.ts，显式 regenerate 生成、§3 留痕；维护者审阅仍缺（G6）】
- [x] 每条 bus 只出现一次；已有事件域不被复制为 ScriptEvents/NativeEvents/ProbeEvents 新事件。【expected 域组集按节点版本条件化（KeyBindEvents >=26），1.21.1/26.1.2/26.2.0 三节点复验】
- [x] TS/Python declaration 与 runtime member、payload 形状、side 和 capability 一致。【本票修 Python scriptDefined post 渲染缺口，三面对齐由同 entry 列表 parity 用例锁定】
- [x] 平台 callback/mixin/transport 时机留在 loader/version Adapter，common 不引入 Minecraft/loader 类型。
- [x] reload 失败或取消后 listener 不双注册、不泄漏到旧 generation，active callback 可见性符合 runtime 语义。【范围边界：listener 层由票 06/07 既有 fixture 判定（动态监听与内置组同走 EventBusJS.execute/PendingListener）；动态事件"定义"面清扫的事务级用例为已登记窄缺口（G7，RELOAD_COMMIT 交接）】
- [x] 不新增万能 Event Module、无生命周期 Point或第二注册路径；旧事件旁路仅在替代 behavior、declaration、trace 通过且无调用者后移除，合法 legacy/raw tier 与公开功能不删除，清理不推迟 final release。【本轮零删除，五条旁路+调用者清单见 REPORT §6（含审查补记 ProbeClassCollector），删除条件移交后续票】
- [x] 随实现交付 Script/Native/Probe 事件的最小可运行示例与必要迁移材料；示例只使用已通过 gate 的 tier、side 和 capability，不把 raw/legacy 示例标成 managed stable。【面拆分：Script 示例真实 Graal e2e；Native/Probe 受 FML/probe 运行时约束以 characterization+golden 承载（G1/G3）；probe 示例注册形状审查后修正（.listen 不存在 → 直接调用，连同两处 master 旧文档源）】

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


## Closure record（2026-09-15）

- 执行者：zcode-agent。实施区间 7fb521eb..a033f202（分支 7 commit + 审查整改 1 commit + merge），
  另有合并后 fix-forward e25b2b85（见下）。
- 交付物：bus 外部行为并发 stress 冻结（EventBusJSExternalBehaviorStressTest 6 用例）、契约反射
  事件派生测试（EventContractReflectorDerivationTest）、ScriptEvents 声明诊断 9 用例 + TS/Py parity
  （含 PythonEventRenderer scriptDefined post 渲染修复——唯一 common 主代码改动）、ProbeEvents golden
  （显式 regenerate）、NativeEvents legacy tier characterization、EventSurfaceOwnership 防复制守卫、
  examples×3 + MIGRATION.md。三类事件面 tier 归属：ScriptEvents 注册 API=managed、动态事件=legacy
  观察；NativeEvents=legacy/raw（STARTUP-only）；ProbeEvents=Probe 扩展面（4 条 SERVER 总线冻结）。
- 双轴审查整改：probe-events.js 及两处 master 旧文档源的 .listen() 幻影 API 修正为直接调用；REPORT
  AC 表与票面 11 条 checkbox 对齐（原 AC7-AC10 实为票面 AC8-AC11、票面 AC7 补专属行）；stress 补
  运行级下界；§6 补记 ProbeClassCollector 消费点；G7 显式登记。
- **五节点合流验证抓出实施/审查盲区**：EventSurfaceOwnershipTest 的 14 域组硬编码在 1.21.1 红
  （KeyBindEvents 是 >=26 组）——fix-forward e25b2b85 按 registerClientEvents 同源守卫条件化，
  1.21.1/26.1.2/26.2.0 三节点复验绿。教训：共享树测试的 expected 集必须与版本守卫同源。
- 测试：common 200 suites/1474 tests 0 失败；:common-api-processor:test、guardLint、五节点 build、
  npm run test:probe-types 全绿；既有 golden/manifest 零变化（唯一新增 probe-events golden 走
  regenerate + §3 留痕）。
- 遗留（REPORT §8）：G1/G3 Native/Probe 示例真机运行证据→主会话 minecraft-mod-mcp；G2 事件进
  portable-core 契约 events 字段→W5 后续票；G4 ScriptEventsJS replacement 不可达→事件面后续票；
  G5 其他节点 check 已由本轮五节点 build 补齐（1.21.1/26.2.0/fabric build 绿；fabric 无
  NativeEvents 面）；G6 golden 维护者审阅→主会话；G7 定义面事务级用例→RELOAD_COMMIT 交接。

票据发布不代表已完成验收或本轮授权源码实施；完成条件与认领规则见本目录索引。
