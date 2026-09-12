# 18: PData 与 ClientData 数据同步路径保护和 generation 边界

**What to build:** 在不改变存档格式、key、wire 和跨 loader 语义的前提下闭合实体 PData 同步与 ClientData 键值同步：PData 持久化容器、dirty/revision/tick 限流、entity id 复用清理和客户端 mirror 保持旧 fixture 证明的现行为；ClientData JSON 校验、大小上限、覆盖写入、断线/切世界清空保持旧 fixture 证明的现行为；接收在 owner 调度下进入当前 generation。

**Blocked by:** [17: 网络注册一次、wire 不变与脚本自定义通道 owner 调度](17-network-sync.md)、[03: 持久化与用户编辑数据保护基线：默认不改、可回滚才迁移](03-data-protection.md)

**Status:** ready-for-agent

**Assignee:** unassigned

**Optional:** false

**Selected:** true

**Human input:** none

**Work items:**

- 固定 NeoForge Entity persistent data 与 Fabric NeoForgeData/NekoJSPersistentData 兼容格式的旧存档回读写入 fixture。
- 保持 PDataSyncPacket id/codec/entity id/revision/NBT wire 不变，验证 dirty 队列、每 tick 上限、空数据清理和 stale revision 拒绝。
- 保持 ClientDataSyncPacket JSON 字符串 wire 与 key/value 语义，验证 JSON 类型限制、32768 字符上限、覆盖、坏包丢弃和断线/切世界清空。
- 把接收与回调消费接到对应 owner 线程/当前 generation，避免 reload 或 close 后写入旧 Context。
- 对比 SERVER reload 前后玩家/实体持久化数据与脚本读取结果，确保 NekoJS 自有 reload 不回滚世界副作用。

## Acceptance criteria

- [ ] 旧 NeoForge 与 Fabric 存档 fixture 中的 NeoForgeData/NekoJSPersistentData 均可回读，PData key、NBT 形状和跨 loader 语义不变。
- [ ] PData 写入触发 dirty 标记，读操作不触发；flush 保持每 tick 上限、tracking player 目标和 revision 递增语义。
- [ ] 空数据包清除对应 entity mirror，stale revision 被拒绝，entity id 复用后不会读到旧实体数据。
- [ ] ClientData 仅接受旧契约允许的 JSON 类型，超限值显式失败，同 key 覆盖，坏包丢弃并记录警告。
- [ ] ClientData 在断线、离开旧世界/切维度时按现约清空；首次进服收到的数据不被进入世界钩子误删。
- [ ] PDataSyncPacket 与 ClientDataSyncPacket 的 id、方向、codec、字段顺序和 JSON/NBT wire 与旧 fixture 一致。
- [ ] SERVER/CLIENT reload、candidate 失败和 root close 后，同步回调不进入旧 Context，持久化数据与客户端 mirror 的保留/清空规则符合各域契约。
- [ ] 若旧 fixture 发现格式必须修复，本票不得直接改默认路径；先补备份、原子替换、版本、旧数据回读、幂等与回滚迁移并纳入验收后再删除旧读路。

## Sources

- [NekoJS 实现票据拆分草案](../implementation-ticket-breakdown.md)
- [运行时生命周期与数据保护规格](../specs/05-runtime-lifecycle-and-data.md)
- [reload 候选状态与线程契约规格](../specs/09-reload-candidate-state-and-thread-contract.md)
- [NekoJS 实施交接单](../implementation-handoff.md)
- [实现票据索引](README.md)

## Dependency rationale

- [17: 网络注册一次、wire 不变与脚本自定义通道 owner 调度](17-network-sync.md): PData/ClientData payload 的注册、wire fixture、owner 队列和 stale generation 处理由网络同步票提供。
- [03: 持久化与用户编辑数据保护基线：默认不改、可回滚才迁移](03-data-protection.md): PData 存档路径、key、跨 loader 格式和旧 fixture 保护回归是修改前后的必备输入。

## Scope and coordination

**Rationale:** PData 与 ClientData 共享“服务端状态到客户端 mirror”的数据同步风险和同一条 owner-thread/generation 边界，合成一个窄数据域可独立验证，且不承担 pack trust 或网络注册重构。

**Coordination:**

- 与 NETWORK_SYNC 共享 payload 注册和主线程 enqueue 文件，但本票只验证 PData/ClientData 数据语义。
- 与 DATA_PROTECTION 共享数据清单；与 language-surface 组的客户端显示消费只做接口协调，不实现 UI。

票据发布不代表已完成验收或本轮授权源码实施；完成条件与认领规则见本目录索引。
