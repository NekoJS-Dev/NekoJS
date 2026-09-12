# 10: 按类型 global、显式 shared 与候选顶层写集联合提交

**What to build:** 在同一个 NekoRuntimeRoot 内为 STARTUP、SERVER、CLIENT、TEST 提供独立 global backing store，同类型多文件和普通 reload 共享；提供显式进程内 shared 工作名入口；global 与 shared 的顶层 set/delete/clear 进入 candidate 写集，支持 read-your-writes、失败丢弃、并发冲突检测和联合提交，不承诺深回滚或网络同步。

**Blocked by:** [09: Managed Surface 单一规范源与声明/Probe 派生链](09-managed-surface.md)、[07: 同类型串行、close 优先与 watchdog 隔离恢复](07-runtime-threads.md)

**Status:** ready-for-agent

**Assignee:** unassigned

**Optional:** false

**Selected:** true

**Human input:** none

**Work items:** 用 root 拥有的按 ScriptType backing store 替换进程级 NekoGlobal 静态 Map，并加入显式 shared 窄域 Map 入口。, 为每个 generation 创建 global/shared 视图，捕获顶层 set/delete/clear 写集并实现 read-your-writes。, commit 前联合预检私有与共享写集；其他 writer 修改同管 key 时让 candidate 失败，成功时一次性发布，并提供同一 candidate 域计划的联合预检/联合成败边界。, 保留 root 级 Map 跨 reload、server stop 和切换世界，root close 释放，generation close 不清空。, 对保存的 guest 函数或 Value 施加 generation 生命周期，不因存入 Map 获得永久保活。, 区分 NekoJS global 状态容器与当前 Context 的 globalThis，并保留现有 Node shim 回归；不新增语言管线。, 为 1.2.0 clean cutover 补旧跨类型 global 到显式 shared 的迁移表，同类型用法保持不变。

## Acceptance criteria

- [ ] 同类型多个脚本文件共享同一 global，同名 key 在 STARTUP、SERVER、CLIENT、TEST 读回彼此独立的值。
- [ ] candidate 内顶层写入可读回；成功 commit 后 active 可见，失败时 set、delete、clear 均不污染旧 active。
- [ ] 其他 writer 在候选期间修改同一受管顶层 key 时冲突被检测且不丢写，candidate 失败；跨类型同名私有 key 不冲突，shared 竞争写入失败。
- [ ] 一次 candidate 同时写 global 与 shared 时，两边联合成功或全部不发布，不存在半提交。
- [ ] 提供不包含领域语义的联合预检边界：测试计划和后续领域计划（如 Item/Block modification）可与 global/shared 写集联合预检并联合成功或失败；本票不实现领域计划，也不把领域 Adapter 拉进 global owner。
- [ ] 随实现交付最小可运行示例与必要迁移材料：同类型 global、显式 shared、旧跨类型用法迁移和失败保留均只用已通过 gate 的能力展示。
- [ ] global/shared 在同一 root 内跨普通 reload、server stop 和切换世界保留；root close 释放；generation close 不误清；独立 root 和测试 runner 从空状态开始。
- [ ] 保存的 guest 函数或 Value 不延长已销毁 Context 的生命周期；嵌套对象、列表、已共享 Java 对象内部修改明确不承诺深回滚。
- [ ] 用户脚本中的 global 与 globalThis 分工可外部观察，Node shim 仍使用正确语言全局对象；不把状态容器当作模块全局对象。
- [ ] 迁移表逐项列出旧跨类型 global 写法到显式 shared 的替代；同类型用法不变，fixture 通过后删除旧隐式跨类型回退和双写。
- [ ] 不新增权限系统、第二 runtime owner、网络同步协议或通用事务框架，也不收紧高级 Java 访问。

## Sources

- [NekoJS 实现票据拆分草案](../implementation-ticket-breakdown.md)
- [运行时生命周期与数据保护规格](../specs/05-runtime-lifecycle-and-data.md)
- [reload 候选状态与线程契约规格](../specs/09-reload-candidate-state-and-thread-contract.md)
- [global 共享状态与候选写入规格](../specs/10-shared-global-candidate-writes.md)
- [NekoJS 实施交接单](../implementation-handoff.md)
- [实现票据索引](README.md)

## Dependency rationale

- [09: Managed Surface 单一规范源与声明/Probe 派生链](09-managed-surface.md): shared 最终公开符号、binding 定义、declaration 与迁移表输入需要 managed surface 冻结，避免先发布第二规范源。
- [07: 同类型串行、close 优先与 watchdog 隔离恢复](07-runtime-threads.md): 并发 writer、同类型串行和 close/watchdog 边界决定联合写集的冲突检测与提交时机。

## Scope and coordination

- **Rationale:** global/shared 是一个窄的 Map 状态域，但包含隔离、事务、生命周期和迁移的完整外部路径；独立 root 和 reload fixture 可直接验证，不需要 registry 或客户端显示域。
- **Coordination:**
  - shared 仍为工作名；最终公开符号、declaration 和完整 managed surface 由 MANAGED_SURFACE 组冻结，重名只需同步 binding 定义与迁移表，不阻塞运行时语义。
  - LANGUAGE_PIPELINE 只需保留现有 Node shim/globalThis 回归，不得让本票依赖新转译或全语言管线。

票据发布不代表已完成验收或本轮授权源码实施；完成条件与认领规则见本目录索引。
