# 票 17：stale packet 与 generation 边界语义表（网络自定义通道）

接收链路（两 loader 同构，差异只在平台 hop 原语）：

```
平台网络线程收包
  ├─ NeoForge: IPayloadContext.enqueueWork（NekoJSNetwork/NetworkMessageHandler 包装）
  └─ fabric : context.server().execute() / context.client().execute()（FabricPlayNetwork receiver）
        ↓ 平台主线程（= 该 ScriptType 的 owner 线程：SERVER=server thread，CLIENT=client/render thread）
NetworkMessageHandler.postServerEvent / postClientEvent（中立投递核心）
        ↓ 按 channel 定向
NetworkEvents.SERVER / NetworkEvents.CLIENT（EventGroup，NekoJSCorePlugin 与 FabricCorePlugin 均注册）
        ↓ 票 06/07 机制约束接收面
当前 active generation 的监听器闭包（isContextDead 三态短路 + noteCallbackEnter/Exit）
```

| 时刻 | 在途/新到达 packet 的行为 | 保证机制 | fixture |
|---|---|---|---|
| active 稳态 | 按 channel 投给 active 监听器，恰好一次 | 总线 dispatch | `serverPacketRoutesToActiveListenerByChannel`、`clientPacketRoutesOnlyToClientBus` |
| reload 候选构建中（commit 前） | 由 active generation 服务；候选监听器在 pendingListeners 收集，不挂总线，不提前接收 | 票 06 candidate 可见性（EventBusJS.execute → collectPendingListener） | `candidatePhasePacketIsServedByActiveAndNeverEntersCandidate` |
| commit 点 | clearListeners(SERVER) 清旧代 → 发布新 runtime → 激活候选监听器；commit 在 owner 线程持锁执行，与同线程的 packet dispatch 天然串行（不重叠） | 票 06 commitGeneration 顺序 + 票 07 owner-thread 串行 | 同上（commit 后断言）|
| commit 后 | 同一事件只由新 generation 处理一次；旧代闭包即便残留也经 isContextDead 判 dead 双保险 | 票 06 commit 清扫 + isContextDead 三态 | `candidatePhasePacketIsServedByActiveAndNeverEntersCandidate`、`consecutiveReloadsKeepOnlyLatestGenerationReceiving` |
| 候选失败/被 close 抢占 | 候选监听器随候选丢弃，从未上总线；active 不受影响，继续接收 | 票 06 discardCandidate | 票 06/07 既有 fixture（本票网络面不重复）；网络侧等价面 = candidate 用例的 active 持续命中断言 |
| active watchdog 隔离（activeFailed） | 监听器闭包 isContextDead 短路：packet 丢弃、不抛、不触碰被终止的 Context/timer/binding；不自建第二个 active；显式 reload 恢复接收 | 票 07 markActiveFailed + isContextDead(contextKilled) | `isolatedActiveFailureDropsInFlightPackets` |
| root/manager close | close 的 fullReloadCleanup 清空该类型全部网络监听器：后续 packet 无监听器可投 → 丢弃、不抛；已关闭 Context eval 抛、ScriptManager 反查抛（未复活） | close 顺序（票 07）+ clearListeners | `closeDropsInFlightPacketsAndNeverTouchesClosedContext`、`repeatedCloseAndLatePacketsAreSafe` |
| 监听器执行体异常 | 总线捕获并经 ScriptErrorReporter 记录，不传播进平台 enqueue 的主线程任务，不中断同 channel 其它监听器 | EventBusJS.post 捕获 + 分发闭包 catch | `listenerExceptionIsContainedAndDoesNotAbortDispatch` |

不触碰已关闭资源的机制注记：网络侧从不直接持有 Context/timer/binding 引用——投递只达总线，
执行面在监听器闭包内，闭包以注册时捕获的 Context 经 isContextDead 判死后短路，
因此「在途 packet 丢弃」不需要网络层自行撤销任何 generation 资源。
