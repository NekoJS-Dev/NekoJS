# 17: 网络注册一次、wire 不变与脚本自定义通道 owner 调度

**What to build:** 把 NekoJS payload 注册保留在 loader 原生初始化时机，通过既有 PlayPacketDispatcher/Adapter 装配发送与接收面；普通 reload 不重复注册 network，以旧 wire fixture 为事实输入，不因文字描述暗增策略；脚本自定义 Network 通道经 runtime owner 调度进入当前 generation，旧 generation 或关闭后的 stale packet 不触碰已关闭 Context。

**Blocked by:** [07: 同类型串行、close 优先与 watchdog 隔离恢复](07-runtime-threads.md)

**Status:** closed

**Assignee:** zcode-agent

**Optional:** false

**Selected:** true

**Human input:** none

**Work items:**

- 固定现有 NekoScriptPayload 与基础 play/configuration payload 的 id、方向、codec 和旧 wire bytes fixture。
- 让 NeoForge 与 Fabric 各自只在 loader 初始化注册一次，reload/root close 不重复注册或注销平台 network。
- 统一接收侧经平台 enqueue/main-thread 入口进入对应 ScriptType owner 队列，再路由当前 active generation。
- 定义 reload commit 前后、active watchdog 隔离和 root close 后的 stale packet 行为：排队到新 generation、丢弃或明确错误，不触碰旧 Context。
- 保留两 loader 当前已支持的网络面子集，不为了 parity 补脚本编辑器、dashboard 或客户端显示域。

## Acceptance criteria

- [x] NeoForge 与 Fabric 的 network payload 注册在启动/客户端初始化各发生一次，多次 SERVER/CLIENT reload 后注册计数和平台连接协商不变。【三面 JVM（compat seam 恰一注册 / 源码 trace 全仓恰 2 处 install + reload 面零注册 / fabric-api 以 IAE 拒绝重复注册）+ 双 loader 方向钉住；平台 event/entrypoint 单次触发为 loader 契约（characterization，in-game smoke 未跑＝REPORT R2）】
- [x] NekoScriptPayload 及本票触达的基础 payload 保持旧 id、方向、codec、字段顺序和线格式；新旧 fixture 字节或等价解码对照一致。【6 payload golden hex（eba89230 实测）逐字节 + decode 等价 + id 断言，五节点全等；审查勘误后 trace 终版 9 用例在 26.2.0/1.21.1/26.2.0-fabric 补齐 9/0】
- [x] 脚本 Network.sendToServer/sendToPlayer/sendToAll 的现有语义不变，接收事件在对应平台主线程/owner 队列执行。【发送面经 PlayPacketDispatcher 装配 + null→空 tag 归一化；接收 hop 断言经审查强化（enqueueWork 调用计数，防回归为网络线程直投）；真机 sendToServer 为 characterization】
- [x] reload commit 前到达的 packet 不提前进入 candidate；commit 后新事件只由新 generation 处理一次，旧 generation 不再接收。【真实 ScriptManager+Graal 管线 8 用例：候选期由 active 服务、commit 后新代恰一次旧代冻结、连续 reload 单调推进】
- [x] active watchdog 隔离或 root close 后，在途 packet 被丢弃或返回明确失效结果，不调用已关闭 Context、timer 或 binding。【watchdog（语句上限）杀 active → isActiveFailed → 丢弃不触碰；close 幂等 + late packet 安全（整改后为行为断言：close 后再投仍零投递）】
- [x] 网络线程异常、非法 channel、超大 payload 和坏 NBT 的行为与旧防线一致，不炸平台网络线程或静默 no-op。【解码边界：channel 长度/空 channel IAE、pack 超限、超长字符串、空 key；坏 NBT 抛（整改补 AssertionError 排除下界，形态说明见 characterization 4）；监听器异常遏制不中断同 channel 后续】
- [x] Fabric 当前缺失的编辑器/显示网络面保持显式子集，不在本票伪造 parity 或接客户端 UI。【源码 trace 恰 6 注册调用/5 类型 + 显示域零引用；FabricPlayNetwork javadoc 显式子集陈述（含审查首段对齐）】
- [x] 重复 dispatcher、绕过 owner 队列的 receiver 或旧直接 Context 路由，仅在全 loader fixture 通过后删除。【审计结论：无可删项——dispatcher 装配恰 2 处（每 loader 一）、receiver 全部先 hop、网络路径无直接 Context 路由；NekoJSCommands 对 ShowErrorListPacket 的显示域直发保留并标注（REPORT §6-R1，不属删除门三类）】

## Sources

- [NekoJS 实现票据拆分草案](../implementation-ticket-breakdown.md)
- [运行时生命周期与数据保护规格](../specs/05-runtime-lifecycle-and-data.md)
- [reload 候选状态与线程契约规格](../specs/09-reload-candidate-state-and-thread-contract.md)
- [NekoJS 实施交接单](../implementation-handoff.md)
- [实现票据索引](README.md)

## Dependency rationale

- [07: 同类型串行、close 优先与 watchdog 隔离恢复](07-runtime-threads.md): 接收侧 owner 队列、close 优先、reload 重入和 watchdog 隔离是 stale packet 与 generation 边界的真实输入。

## Scope and coordination

**Rationale:** 本票只覆盖网络所有权、wire 兼容和脚本自定义通道，不包含 PData/ClientData payload 域或 pack trust；两 loader 旧 wire 与 stale generation fixture 使其独立可验。

**Coordination:**

- DynamicRegistry 多人侧只有真实协议输入才依赖本票结果；candidate plan 本身不阻塞本票。
- PACK_TRUST 复用配置期 payload 桥，但信任语义在 PACK_TRUST 内验证。

## Closure record（2026-09-15）

- 执行者：zcode-agent。实施区间 eba89230..e0820f30（实现 5 commits + 审查整改 2 commits b6fddf74/e0820f30），合并 9bfaf7d7。
- 交付物：**主源码行为零改动**（唯一主源文件改动是 FabricPlayNetwork javadoc 子集陈述修正）+ 44 个 fixture 用例——wire golden 13（6 payload 的 id/encode-hex/decode 三面）、routing 8（真实 ScriptManager+Graal 的 owner 路由/generation/stale）、defense 9、source trace 9（终版）、compat shape 2（hop 计数强化）、fabric registration-once 2（节点本地分发）。
- 双轴审查整改（必修-1 + 优化 2-7，REPORT §9）：跨节点证据勘误（3 节点 trace 终版补跑 9/0 + † 标注）；hop 断言强化；坏 NBT AssertionError 排除下界；fabric once 测试 @AfterEach 恢复 NOOP；trace 扫描纳入 common/src/main；重复 close 用例自证断言换行为断言；javadoc 首段对齐。
- **并行测试 flake 根治**：整改验证中复现 RoutingTest「候选脚本零执行」——根因是固定名 tmp gameDir（`nekojs-query-tools-test`/`nekojs-kb-test`/`nekojs-smoke-test`）+ Platform 初始化先到先得寄生，并行 test JVM（双 fabric 节点/跨 worktree）共享固定名目录互清脚本 fixture；修复 = `TestGameDirs.unique(base+PID)` 统一替换全部固定名 gameDir，双 fabric 全套连续 7 轮并行全绿。教训入 REPORT §6-R5（org.gradle.parallel=true 下任何固定名 tmp 目录都是雷）。
- 测试：双 fabric 全套 114/0 ×7 轮并行；三节点全套 220/220/139 全 0 失败；common check / guardLint（253 块 / 399 文件 / 0 警告）/ 五节点全量 build 主会话合并门全绿。
- 遗留（REPORT §6）：R1 NekoJSCommands 显示域直发 → 显示域后续票；R2 in-game 注册计数 smoke → 主会话 minecraft-mod-mcp；R4 routing harness ~150 行 common 装配复刻 → W4 测试基建收口。

票据发布不代表已完成验收或本轮授权源码实施；完成条件与认领规则见本目录索引。
