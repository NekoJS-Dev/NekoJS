# 票 18：PData dirty/revision/tick 限流语义表

依据：`src/main/java/com/tkisor/nekojs/wrapper/pdata/PDataSyncService.java`（revision 9da3abf3 前后无 diff 的行为面 + 本票 joinLevel 修复）、`PersistentDataJS.java`、`PDataSyncListener.java`、`NekoJSClient.java`、`src/fabric/java/com/tkisor/nekojs/fabric/FabricPDataSync.java`。fixture 引用为本票新增/既有测试类。

## 1. dirty 队列（写入触发、读不触发）

| 面 | 语义 | 机制锚点 | fixture |
|---|---|---|---|
| 写触发 dirty | 所有写操作（putXxx/remove/edit/merge/replaceTag/clear）经 `PersistentDataJS.saveTag` 统一 fire `dirtyMarker`；服务端装配 dirtyMarker = `PDataSyncService.markDirty(entity)` → 入 `DIRTY_ENTITIES`（IdentityHashMap-backed 同步 Set，实体身份语义；仅服务端 `!isClientSide`） | `PersistentDataJS.saveTag:150-156`、`PDataSyncService.markDirty:35-37` | `PersistentDataJSTest.writesFireDirtyMarkerButReadsAndExplicitSyncDoNot` |
| 读不触发 | 读走 `getTag()` 不经 saveTag；数组读返回防御拷贝（既有回归） | `PersistentDataJS.getTag/getByteArray...` | 同上 + 既有 `arrayGettersReturnDefensiveCopiesAndDoNotMarkDirty` |
| 显式 sync() | `syncer` = `PDataSyncService.syncNow`：立即 send + 出脏队列，不重复记 dirty | `PDataSyncService.syncNow:40-44` | 同上（syncer/dirty 计数分离断言） |

## 2. flush（每 tick 上限）与 tracking 目标

| 面 | 语义 | 机制锚点 | fixture |
|---|---|---|---|
| flush 时机 | 每 server tick 末（NeoForge `ServerTickEvent.Post` → `PDataSyncListener.onServerTickPost`；fabric `ServerTickEvents.END_SERVER_TICK` → `FabricPDataSync.flush`） | `PDataSyncListener:26-33`、`FabricPDataSync:45,87-89` | characterization（真实 tick/Entity 不可裸 JVM） |
| 每 tick 上限 | `MAX_SYNCS_PER_TICK=256`：flush 先清无效脏实体（null/isRemoved/clientSide），再按上限发送，超出者留到下一 tick | `PDataSyncService.flush:47-58` | characterization；行为锚点为实读（本票未改该循环） |
| tracking 目标 | 发送统一走 `PlayPacketDispatchers.get().sendToPlayersTrackingEntityAndSelf(entity, packet)`（tracking 该实体的玩家 + 实体自身）；fabric dispatcher = `PlayerLookup.tracking(entity)` + self 的 canSend 守卫发送，NeoForge dispatcher 平台原生 | `PDataSyncService.send:116`、`FabricPlayNetwork.FabricDispatcher:123-131` | 票 17 `NetworkPayloadDefenseLineTest`（dispatcher 装配语义）+ wire golden |
| 超限跳过 | tag 字符串化 > 32768 字符 → 不发送 + WARN（`MAX_SYNC_TAG_CHARS`） | `PDataSyncService.send:110-113` | characterization（WARN 面见 REPORT） |

## 3. revision 与 mirror 生命周期

| 面 | 语义 | 机制锚点 | fixture |
|---|---|---|---|
| revision 递增 | 每次服务端发送 `SERVER_REVISIONS.merge(id, 1, Integer::sum)`（首次发送即为 1）；服务端账本按 entity id | `PDataSyncService.send:115` | 客户端单调接受面反推（`PDataSyncAcceptTest`）+ 实读（JVM 无真实 Entity 驱动服务端发送） |
| stale revision 拒绝 | 客户端 `packet.revision < currentRevision` → 丢弃；**等于** currentRevision → 接受（重发幂等覆盖，既有契约保留） | `acceptClientSync:90-99` | `PDataSyncAcceptTest.staleRevisionIsIgnored` / `emptyDataClearsAndEqualRevisionOverwrites`；reload 无关性：`DataSyncGenerationBoundaryTest.receiveFaceFeedsMirrorsIndependentOfGenerations` |
| 空数据包清 mirror | 空 tag 包 = 清除该 entity 的 mirror（+revision 记账推进） | `acceptClientSync:95-96` | `PDataSyncAcceptTest.emptyDataClears...` |
| entity id 复用 | 实体离开 level（NeoForge `EntityLeaveLevelEvent` / fabric `ENTITY_UNLOAD`）→ `onEntityRemoved`：revision+1 发**空包**（跟踪客户端清 mirror）+ 服务端 `SERVER_REVISIONS.remove(id)` + 出脏队列——新实体复用同 id 从 revision 1 重启、客户端无旧 mirror 可读 | `PDataSyncService.onEntityRemoved:65-72`、`PDataSyncListener:36-38`、`FabricPDataSync:48-49` | JVM 面：空包清除 + revision 去重（如上）；离开事件的触发面 = characterization（平台事件） |
| 断线/切世界全清 | NeoForge：`ClientLevelEvent.Unload`(client) → `clearClientMirrors()`；fabric：`ClientPlayConnectionEvents.DISCONNECT` + `ClientLevelWatch`（离开旧世界才清） | `NekoJSClient.onLevelUnload:59-65`、`FabricPDataSync.registerClient:92-108` | `PDataSyncAcceptTest.clearClientMirrorsWipesEverything` + fabric `ClientLevelWatchTest` |
| mirror 只读 | 客户端 mirror 经 `PersistentDataJS.readOnly` 包装，写抛 `UnsupportedOperationException` | `EntityExtension.neko$pdata:68-70`、`PersistentDataJS:150-156` | 既有契约（03 盘点 §4） |

## 4. joinLevel 窗口（本票修复）

- 修复前（03 §3-3）：`EntityPDataStore` 按 id 反查实体（`pdataContainer`/`findEntity` 遍历 `level.getEntity(id)`），`EntityJoinLevelEvent` 窗口内实体未入索引 → `Access.set` 静默 no-op、`Access.get` 返回空 tag。
- 修复后：`Access` 增加实体引用面（default 委托 id 面），两平台 install override 为**直接解引用容器**（NeoForge `entity.getPersistentData()` / fabric `((NekoEntityPData) entity).neko$getPDataRoot()`）；`EntityExtension.neko$pdata` 经 `getPDataTag/setPDataTag` 走实体引用面——join 窗口写入直接落容器、随实体存档。
- 钉住：`PDataJoinWindowResolutionTraceTest`（4 用例：桥路由 + 两平台 override + 反查调用计数保持 id 面专属 + 拷贝/移除语义同形）；真机 joinLevel 事件行为 = characterization（REPORT）。
