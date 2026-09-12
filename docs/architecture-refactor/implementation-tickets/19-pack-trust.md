# 19: 脚本包分发 trust 决策、远端包激活与 Fabric WORLD 现状

**What to build:** 以旧 fixture 为事实输入，闭合 GLOBAL/WORLD 脚本包在服务器 gather、配置期哈希/Bundle 传输、客户端验证或信任决策、SERVER_CACHE 激活和断线卸载的路径：未信任服务器不执行远端脚本，hashOnly 只观测不执行，显式信任后按既有原子持久化语义保存；trust-store 跨 reload 保留，损坏降级可诊断；客户端包变更通过 root 触发 CLIENT reload。Fabric WORLD 保留当前行为并显式记录差异，不新增签名政策或权限，也不强迫 parity。

**Blocked by:** [17: 网络注册一次、wire 不变与脚本自定义通道 owner 调度](17-network-sync.md)、[03: 持久化与用户编辑数据保护基线：默认不改、可回滚才迁移](03-data-protection.md)

**Status:** ready-for-agent

**Assignee:** unassigned

**Optional:** false

**Selected:** true

**Human input:** none

**Work items:**

- 复用 PackSyncServer/Client 共享管线，固定 enabled+clientSync 的 GLOBAL/WORLD gather 顺序和 hash-list/bundle 次序。
- 验证客户端信任或拒绝结果、hashOnly 不执行、bundle 大小/损坏/验签失败、信任提示和断线卸载的外部输出。
- 保持 trusted-servers.json 路径、JSON key、原子写入、reload 保留和损坏降级语义；签名公钥 pinning 现状不被加强或放松。
- 让接受的 SERVER_CACHE pack 只在 bundle 完整落盘并验证后激活，再经 root reload CLIENT；断线卸载 cache 集合但不删除可再生文件。
- 记录 Fabric WORLD 当前激活/分发差异并给出能力证据；不新增本地 GLOBAL/WORLD 签名政策，不要求 Fabric parity。

## Acceptance criteria

- [ ] 服务器按旧顺序只收集启用且 clientSync 允许的 GLOBAL/WORLD 包，配置期先发送 hash list，非 hashOnly 且有包时再发送 bundle。
- [ ] 客户端对未信任服务器明确断开或拒绝执行并给出 trust hint；hashOnly 模式不落盘执行远端脚本；all 模式仅在验证与信任通过后激活。
- [ ] 显式 trustServer/trustPublicKey 的路径、JSON key、bucket 计算、原子替换和跨 reload 保留行为不变；损坏文件降级为空 store 并产生可观察警告。
- [ ] bundle 损坏、hash 不匹配、超限或非法 manifest 的远端包被拒绝，不执行脚本，不覆盖既有本地包，失败原因进入 pack trust 结果。
- [ ] 接受的远端包写入现有 SERVER_CACHE bucket，激活后经 root 触发 CLIENT candidate reload；断线或服务器清空时卸载 cache 集合，可再生文件按现约保留。
- [ ] Fabric WORLD 的当前激活、列表和分发现象被 fixture 固定并公开为 partial/unavailable 证据；NeoForge 与 Fabric 不伪造 parity，也不改变本地 pack 默认启用或路径。
- [ ] trust 决策、拒绝、降级和审计输出在执行或 pack sync 结果中可见，不宣称强恶意隔离。
- [ ] 共享核心管线 fixture 覆盖 NeoForge 与 Fabric 当前配置期桥；loader 重复信任解析/写文件路线在两侧行为等价验证后才删除。

## Sources

- [NekoJS 实现票据拆分草案](../implementation-ticket-breakdown.md)
- [运行时生命周期与数据保护规格](../specs/05-runtime-lifecycle-and-data.md)
- [NekoJS 实施交接单](../implementation-handoff.md)
- [实现票据索引](README.md)

## Dependency rationale

- [17: 网络注册一次、wire 不变与脚本自定义通道 owner 调度](17-network-sync.md): 配置期 payload 传输、wire 兼容、owner 队列和断线处理是远端包验证与激活的真实输入。
- [03: 持久化与用户编辑数据保护基线：默认不改、可回滚才迁移](03-data-protection.md): trust-store、pack 状态、缓存与用户数据的路径/格式旧 fixture 及保护回滚验收是持久化决策的必备输入。

## Scope and coordination

**Rationale:** pack trust 是独立于 PData/ClientData 的远端脚本执行安全路径；用一次客户端连接夹具可从服务器包集合观察到信任、激活、reload 与断线卸载全链路。

**Coordination:**

- 与 DATA_PROTECTION 共享 trust-store 与 cache 分类；冲突协调而非硬串。
- 能力表最终呈现由 language-surface/MANAGED_SURFACE 组承接，本票提供行为证据。
- RUNTIME_COMMANDS 只复用 trust 命令结果，不改 trust 语义。

票据发布不代表已完成验收或本轮授权源码实施；完成条件与认领规则见本目录索引。
