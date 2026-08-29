# 09: Fabric 实体扩展机制 + pdata

**What to build:** fabric 上的实体扩展面：`EntityExtension` / `LivingEntityExtension` 去 neoforge 守卫
所需的两块地基 —— ①实体持久化数据存储（NeoForge 的 `Entity#getPersistentData()` 等价物）；
②扩展接口注入（NeoForge 走 `nekojs.interface_injection.json`，fabric 需 mixin + fabric-loom
interface injection 或等价方案）。地基就位后 `player.pdata()` / `entity.neko$*` 在 fabric 可用，
`PDataSyncService`（发送面已在票 05 中立化）随之接通。

**Blocked by:** 05（play 阶段通道底座已就位：`PlayPacketDispatcher` + `PDataSyncPacket` 只差 fabric 注册）。

**Status:** ready-for-agent

**从票 05 带过来的事实**
- `PDataSyncService` 的三处发送已走 `PlayPacketDispatcher`，fabric 实现已可用；
  缺的只是 `PayloadTypeRegistry.clientboundPlay().register(PDataSyncPacket...)` + 客户端 receiver
  （对齐 `NekoJSNetwork#handlePDataSyncOnClient` → `PDataSyncService.acceptClientSync`）。
- 真正的阻塞点是 `EntityExtension`（`src/main/java/com/tkisor/nekojs/api/inject/EntityExtension.java`，
  整文件 `//? if neoforge`）：`neko$pdata()` 读写靠 `self().getPersistentData()`，那是 NeoForge 给
  `Entity` 加的 API，vanilla / fabric 没有。
- 每 server tick 的 `PDataSyncService.flush(server)` 与实体移除时的 `onEntityRemoved(entity)`
  在 fabric 侧也要挂钩（`ServerTickEvents.END_SERVER_TICK` / `ServerEntityEvents.ENTITY_UNLOAD`）。

- [ ] fabric 实体持久化数据存储（mixin 加字段 + save/load 钩子）
- [ ] `EntityExtension` 去守卫（或拆出中立子集）并在 fabric 注入到 `Entity`
- [ ] `PDataSyncPacket` fabric 注册 + 客户端 receiver + flush/onEntityRemoved 钩子
- [ ] pdata 服务器→客户端同步在 fabric 真机可验证
- [ ] 四节点编译 + 测试 + guardLint 绿
