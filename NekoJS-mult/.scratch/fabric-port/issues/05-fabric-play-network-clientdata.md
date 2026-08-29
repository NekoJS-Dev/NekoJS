# 05: Fabric play 阶段通道底座 + ClientData 推送

**What to build:** play 阶段网络的 fabric 面：中立发送通道 `PlayPacketDispatcher`（共享树业务侧只依赖它）
+ S2C payload 类型注册与客户端 receiver。落地 `ClientData.sync` / `clientData.get` 全链路。

**Blocked by:** 04。

**Status:** done

- [x] play 阶段发送面中立化：`PlayPacketDispatcher`（sendToPlayer / sendToAllPlayers /
      sendToPlayersTrackingEntityAndSelf），NeoForge 装 `PacketDistributor`、fabric 装
      `ServerPlayNetworking` + `PlayerLookup`；`ClientDataSyncJS` 与 `PDataSyncService` 改走它
- [x] `ClientDataSyncJS` 去掉 neoforge 守卫，fabric 侧经 `FabricCorePlugin` 注册 `ClientData` 绑定
      （只读侧 `clientData` 早已由 common 内置插件注册）
- [x] fabric 真机验证（dedicated server + runClient --quickPlayMultiplayer localhost）：
      服务器脚本在 `PlayerEvents.loggedIn` 里 `ClientData.syncTo` + `ClientData.sync`，客户端脚本
      读到 `direct hello=world n=42` 与 `broadcast=[a, b, c]`
- [x] 四节点编译 + 测试 + guardLint 绿

**踩坑记录**
- `ServerPlayConnectionEvents.JOIN` 的语义是"可以给这个连接发包了"，fabric 在
  `PlayerList#placeNewPlayer` 中途触发它，此时玩家**还没进** `server.getPlayerList()`。
  于是 `loggedIn` 里的全服广播（`ClientData.sync`）静默漏掉刚进来的这个人：直推 `syncTo` 到
  同一个玩家能通（`canSend=true`），而 `PlayerLookup.all(server).size()` 是 0。
  已把 `loggedIn` 改为排到下一个 tick 末（`END_SERVER_TICK` 前先抽干待发队列）——与 NeoForge
  `PlayerLoggedInEvent`（进列表之后）对齐。`server.execute` **不行**：同线程会内联执行，
  等价于没有延迟；`tell(TickTask)` 非 public，故用自己的队列。队列在 `SERVER_STOPPED` 清空，
  `DISCONNECT` 时先撤排队项（同一 tick 进又出不会出现 loggedOut 早于 loggedIn）。
- `clientData.get` 返回的是 Java `Map`/`List`（common 的 `jsonToObject`），所以
  `JSON.stringify(v)` 得到 `{}`；脚本按成员访问（`v.hello`）或转字符串。
  另：成员校验（scriptMemberValidation）对 `Object` 上的 `.size()`/`.hello` 会报
  "no member ... on Object"，只是预检日志，不阻断执行。
- `clientData` 的清空时机：NeoForge 挂 client level unload（断线与切维度都清）。fabric 没有
  对应事件，改成盯 `Minecraft#level` 实例变化——但只在"离开一个已有世界"时清
  （`lastClientLevel != null`）。第一版无条件清，结果进服那次 `null→世界` 的变化把刚随进服
  推下来的数据一起抹掉了（冒烟直接从"两条都到"退化成"一条都没有"）。
- `PlayPacketDispatcher` 只是接口，装配点单独放 `PlayPacketDispatchers`（与 `Platform` /
  `Diagnostics` 同形）；未装配时走 NOOP 丢弃并首次 WARN，发送失败不抛异常不打断脚本。

**pdata 未做，拆出票 09**：`PDataSyncService` 的发送面已中立，但读写面依赖
`EntityExtension`（整文件 neoforge 守卫）与 NeoForge 的 `Entity#getPersistentData()` ——
fabric 上二者都没有，需要先落地实体扩展机制 + 实体持久化数据存储。见票 09。
