# 17: 网络注册一次、wire 不变与脚本自定义通道 owner 调度

**What to build:** 把 NekoJS payload 注册保留在 loader 原生初始化时机，通过既有 PlayPacketDispatcher/Adapter 装配发送与接收面；普通 reload 不重复注册 network，以旧 wire fixture 为事实输入，不因文字描述暗增策略；脚本自定义 Network 通道经 runtime owner 调度进入当前 generation，旧 generation 或关闭后的 stale packet 不触碰已关闭 Context。

**Blocked by:** [07: 同类型串行、close 优先与 watchdog 隔离恢复](07-runtime-threads.md)

**Status:** in-progress

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

- [ ] NeoForge 与 Fabric 的 network payload 注册在启动/客户端初始化各发生一次，多次 SERVER/CLIENT reload 后注册计数和平台连接协商不变。
- [ ] NekoScriptPayload 及本票触达的基础 payload 保持旧 id、方向、codec、字段顺序和线格式；新旧 fixture 字节或等价解码对照一致。
- [ ] 脚本 Network.sendToServer/sendToPlayer/sendToAll 的现有语义不变，接收事件在对应平台主线程/owner 队列执行。
- [ ] reload commit 前到达的 packet 不提前进入 candidate；commit 后新事件只由新 generation 处理一次，旧 generation 不再接收。
- [ ] active watchdog 隔离或 root close 后，在途 packet 被丢弃或返回明确失效结果，不调用已关闭 Context、timer 或 binding。
- [ ] 网络线程异常、非法 channel、超大 payload 和坏 NBT 的行为与旧防线一致，不炸平台网络线程或静默 no-op。
- [ ] Fabric 当前缺失的编辑器/显示网络面保持显式子集，不在本票伪造 parity 或接客户端 UI。
- [ ] 重复 dispatcher、绕过 owner 队列的 receiver 或旧直接 Context 路由，仅在全 loader fixture 通过后删除。

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

票据发布不代表已完成验收或本轮授权源码实施；完成条件与认领规则见本目录索引。
