# 05: 单 owner 预整理：闭合两个 loader 的运行时生命周期入口

**What to build:** 以现有 startup、SERVER、CLIENT、afterInit、reload 和 close 路径验证单一 NekoRuntimeRoot 装配与调用者注入，迁移并删除 static root 旁路；保留现有行为，不同时引入第二 owner 或半套新旧 runtime。

**Blocked by:** [01: P0 五节点构建与契约基线](01-build-baseline.md)、[02: P0 独立性能基线](02-perf-baseline.md)、[03: 持久化与用户编辑数据保护基线：默认不改、可回滚才迁移](03-data-protection.md)

**Status:** ready-for-agent

**Assignee:** unassigned

**Optional:** false

**Selected:** true

**Human input:** none

**Work items:** 以 BUILD_BASELINE 记录两个 loader 的当前创建顺序、STARTUP/SERVER/CLIENT/afterInit 时机和 close 行为。, 建立生产 static 可变生命周期状态总账，并归类为 loader/process-owned、root-owned、generation-owned、domain adapter-owned、test-only seam 或待删除 legacy bypass。, 抽出共享装配函数或工厂，只作为构造实现，不新增 RuntimeKernel、gateway、service locator 或第二 owner。, 让 loader entry 私有持有 root，并向 client、server、command、pack sync 和错误边界注入窄生命周期 handle。, 迁移所有生产代码中的公开 RUNTIME_ROOT 直接读取，保留 loader 特有时机与平台接线。, 补当前行为烟测：startup、server started/reload、client load/reload、afterInit 和 close；至少覆盖两个 loader 的现行节点。, 在删除旧旁路前确认 Plugin Runtime 仍只在 loader bootstrap 创建一次。

## Acceptance criteria

- [ ] 生产 static 可变生命周期状态总账覆盖 runtime access、plugin runtime current、plugin entries、shared engine、platform/path/compiler/schema/event callback 状态及 loader 侧 server/level/event/registry 状态；每项有 owner 分类、生命周期、可替换性和测试或删除理由。
- [ ] `NekoRuntimeAccess`、`NekoPluginRuntime.current`、`NekoSharedEngine` 与其他被登记状态不能形成第二 runtime owner 或绕过 root 的生命周期；进程级例外必须可重复测试，root-owned 状态随 root close 释放，独立测试 root 不互相污染。
- [ ] NeoForge 与 Fabric 各自只创建一个 NekoRuntimeRoot，loader entry 外没有可替换或可读取的公开 static root。
- [ ] STARTUP 首次加载、SERVER started 后加载或 reload、CLIENT 在各自 loader 既有安全点加载、afterInit 触发顺序与旧烟测一致。
- [ ] SERVER reload 与 CLIENT reload 都只经 root 生命周期入口发生，命令、F3+T、pack sync 和平台 listener 不再直接触碰 ScriptManager。
- [ ] close 按当前契约冲刷并关闭 script managers、清理 listener 和 root 资源；关闭中的异常不阻止后续清理，重复 close 不产生二次回调。
- [ ] 两 loader 现行 startup/server/client/close 烟测可复现，输出脚本 marker、错误计数、资源释放日志和最终退出状态。
- [ ] Plugin Runtime bootstrap、平台事件注册、network 注册次数在 reload 前后保持一次，不因共同装配函数产生第二套状态。
- [ ] config、world、pdata、pack、trust-store、workspace/declaration 的路径、格式、key、wire 和默认启用规则与基线一致。
- [ ] 公开 static root 旁路、重复 manager 容器和旧直接装配路线在全部调用者迁移并通过上述烟测后同票删除，不保留长期并行路径。

## Sources

- [NekoJS 实现票据拆分草案](../implementation-ticket-breakdown.md)
- [维护者模块设计与运行时所有权规格](../specs/01-maintainer-module-design.md)
- [运行时生命周期与数据保护规格](../specs/05-runtime-lifecycle-and-data.md)
- [reload 候选状态与线程契约规格](../specs/09-reload-candidate-state-and-thread-contract.md)
- [NekoJS 实施交接单](../implementation-handoff.md)
- [实现票据索引](README.md)

## Dependency rationale

- [01: P0 五节点构建与契约基线](01-build-baseline.md): 两 loader 共同装配重排前需要五节点旧行为、旧契约和数据输入基线，避免用当前实现猜测现行时机。
- [02: P0 独立性能基线](02-perf-baseline.md): 共同装配与启动/加载时机预整理会改变相关源码行为；旧启动、reload 和资源采样必须在改动前完成，不能改后再补基线。
- [03: 持久化与用户编辑数据保护基线：默认不改、可回滚才迁移](03-data-protection.md): root 会触达 config、world、pdata、pack、trust-store 与 workspace/declaration 的生命周期；关闭本票的数据一致性验收必须消费 03 的清单和旧 fixture，不能在保护边界未冻结时改写或重建用户数据。

## Scope and coordination

- **Rationale:** 这是所有运行时路径的共同 owner 预整理，范围只含生命周期接线和旁路删除；两 loader 烟测让它独立可验，且不把 registry、语言或 feature 域拉进前置依赖。
- **Coordination:**
  - 与 NETWORK_SYNC、PACK_TRUST、DATA_SYNC、RUNTIME_COMMANDS 共享 loader entry、client reload hook 和 lifecycle handle 文件；按冲突协调，不把 feature 域变成硬依赖。
  - REGISTRY_STARTUP 只消费 root 的启动时机，不阻塞本预整理。

票据发布不代表已完成验收或本轮授权源码实施；完成条件与认领规则见本目录索引。
