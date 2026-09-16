# 18: PData 与 ClientData 数据同步路径保护和 generation 边界

**What to build:** 在不改变存档格式、key、wire 和跨 loader 语义的前提下闭合实体 PData 同步与 ClientData 键值同步：PData 持久化容器、dirty/revision/tick 限流、entity id 复用清理和客户端 mirror 保持旧 fixture 证明的现行为；ClientData JSON 校验、大小上限、覆盖写入、断线/切世界清空保持旧 fixture 证明的现行为；接收在 owner 调度下进入当前 generation。

**Blocked by:** [17: 网络注册一次、wire 不变与脚本自定义通道 owner 调度](17-network-sync.md)、[03: 持久化与用户编辑数据保护基线：默认不改、可回滚才迁移](03-data-protection.md)

**Status:** closed

**Assignee:** zcode-agent

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

- [x] 旧 NeoForge 与 Fabric 存档 fixture 中的 NeoForgeData/NekoJSPersistentData 均可回读，PData key、NBT 形状和跨 loader 语义不变。【fabric 侧生产 mixin 真实方法体 fixture ×2 节点（7 用例/节点，经 vanilla TagValueInput/Output）+ literal 旧 NeoForge 存档回读（含第三方 mod 键不泄漏断言）；NeoForge 读路径 = trace + 既有 EntityPDataStoreTest（bare JVM 无法构造 patched Entity，characterization 如实声明）】
- [x] PData 写入触发 dirty 标记，读操作不触发；flush 保持每 tick 上限、tracking player 目标和 revision 递增语义。【写/读/sync 分离 JVM 钉住（PersistentDataJSTest 新用例与 saveTag 生产语义逐条核对）；flush 256/tick、tracking、revision 递增 = 实读语义表 + 票 03 真机 + 票 17 dispatcher 装配（PDataSyncService 本票零 diff）】
- [x] 空数据包清除对应 entity mirror，stale revision 被拒绝，entity id 复用后不会读到旧实体数据。【PDataSyncAcceptTest 四方法（empty 清 mirror/同 revision 幂等覆盖/stale 拒绝/clear 全清）×4 节点；id 复用防线（onEntityRemoved 空包 + SERVER_REVISIONS.remove）语义表锚点行号核对；事件触发面 characterization】
- [x] ClientData 仅接受旧契约允许的 JSON 类型，超限值显式失败，同 key 覆盖，坏包丢弃并记录警告。【发送面 6 用例与 createPacket 生产代码逐条对上（32768 恰过/超限抛/不发送）；双 loader 接收面坏包丢弃/覆盖/JSON null 各 3 用例（NeoForge hop 计数断言）；WARN 文本不断言记为 characterization】
- [x] ClientData 在断线、离开旧世界/切维度时按现约清空；首次进服收到的数据不被进入世界钩子误删。【fabric 侧 ClientLevelWatch 状态机 JVM 钉住（从两处内联 lambda 提取、逐点等价、instances 独立）；NeoForge onLevelUnload 实读（断线/切维度都触发、首次进服无 unload）+ 既有覆盖/清空 fixture】
- [x] PDataSyncPacket 与 ClientDataSyncPacket 的 id、方向、codec、字段顺序和 JSON/NBT wire 与旧 fixture 一致。【引用票 17 PayloadWireFormatGoldenTest 零改动（两 hex 与本票 evidence 引用逐字符一致核对）；本票 diff 零 payload/codec/网络/mixin 存档文件】
- [x] SERVER/CLIENT reload、candidate 失败和 root close 后，同步回调不进入旧 Context，持久化数据与客户端 mirror 的保留/清空规则符合各域契约。【真 ScriptManager+Graal harness 7 用例（含 while(true) 语句预算真实事务失败）；接收面结构性无 Context；closed Context eval 抛错有反证断言】
- [x] 若旧 fixture 发现格式必须修复，本票不得直接改默认路径；先补备份、原子替换、版本、旧数据回读、幂等与回滚迁移并纳入验收后再删除旧读路。【未触发：全部旧 fixture 零格式改动回读一致；无读路径删除（id 面 Access 保留即证据）】

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

## Closure record（2026-09-16）

- 执行者：zcode-agent。实施区间 9da3abf3..2601b03d（实现 3 commits，无审查整改——双轴审查判定「可合并」，零必修），合并 0c81d49a。
- 交付物：**唯一生产行为改动 = 票 03 §3-3 登记的 joinLevel 窗口写 pdata 静默丢弃缺口修复**（EntityPDataStore.Access 实体引用面 default 方法 + NeoForge/fabric 两处 override，各 <20 行；写入不再依赖 `level.getEntity(id)` 反查）；`ClientLevelWatch` 提取（两域共用、可 JVM 测试）；40 个新 fixture（fabric mixin 存档形状 ×2 节点、joinLevel 修复 trace、generation 边界真 Graal harness、ClientData 域语义、fabric 接收面、状态机）。存档 key/NBT 形状/wire/JSON 契约零改动（diff + 票 17 golden 双重核实）。
- 审查专项结论：证据数字与 XML 首次逐格吻合（9 测试类 ×5 节点）、审查者独立复跑 6 类全绿、三处生产 diff 逐行核过、ClientLevelWatch 提取逐点等价、literal 旧存档含第三方键可信。
- 测试：:26.1.2 271/0/36skip；:26.1.2-fabric 174/0/8skip（含 verifyFabricRuntimeArtifact）；五节点专项（26.2.0/1.21.1/26.2.0-fabric 补跑）逐类 0 失败；五节点全量 build 主会话合并门全绿；common check/guardLint 绿。
- 遗留（REPORT §6）：R2/R6 真机 joinLevel 窗口 + 断线/切维度清空 smoke → 主会话 minecraft-mod-mcp（与票 17 R2 同口径）；R3 id 面 Access 保留无生产调用者（接口契约锚点，收口归后续票）；R4 同 revision 重发幂等覆盖按现行为钉住。

票据发布不代表已完成验收或本轮授权源码实施；完成条件与认领规则见本目录索引。
