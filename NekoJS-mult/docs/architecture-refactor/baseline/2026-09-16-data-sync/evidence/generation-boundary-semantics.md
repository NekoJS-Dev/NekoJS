# 票 18：数据同步域的 generation 边界语义表

核心事实：PData 客户端 mirror（`PDataSyncService` 的静态 CLIENT_* Map）与 ClientData 存储
（`ClientDataStore.SHARED`）都是**进程级 Java 状态**——接收面（`acceptClientSync` /
`ClientDataStore.accept`）只写这些 Map，**从不进入任何 Graal Context**。脚本侧经
`clientData`/`entity.pdata`(客户端只读 mirror) 绑定读取同一状态。因此：

- reload/candidate 失败/close 影响**脚本回调面**（监听器闭包、Context），不影响**数据面**；
- 清空权只属于平台域钩子（断线/离开旧世界），不属于脚本生命周期（reload/close 从不清空）；
- 「同步回调不进入旧 Context」在本域是结构性满足：接收路径没有 Context 可进（对照票 17
  的脚本自定义通道：那里监听器闭包要进 Context，靠 isContextDead 三态短路；本域无此需求）。

| 时刻/事件 | PData mirror | ClientData store | 实体 pdata（服务端容器） | 机制 | fixture |
|---|---|---|---|---|---|
| SERVER reload（成功） | 保留 | 保留 | 保留（随实体存档）；脚本读取结果对照一致 | 数据面是进程级；reload 只重建脚本环境 | `DataSyncGenerationBoundaryTest.serverReloadKeepsClientStoresAndScriptReadsStable`、`serverReloadDoesNotRollBackEntityPData`、`persistentDataJSKeepsServingTheSameContainerAcrossReloads` |
| CLIENT reload（F3+T 同型） | 保留 | 保留 | —（服务端域） | 同上 | `clientReloadKeepsClientStores` |
| candidate 失败（语句上限杀候选等事务失败） | 保留 | 保留 | 保留 | 失败只关闭候选资源（票 06），active 原样服务 | `failedCandidateKeepsStoresAndActiveReads` |
| root/manager close | 在途包仍可安全 accept（不抛、写入生效、可读）；脚本回调面随 close 清空（包无人接、不抛） | 同左 | — | 接收面与 Context 正交；close 清监听器 | `closeKeepsReceiveFaceSafeAndNeverTouchesClosedContext`（含已关闭 Context eval 抛的反证） |
| watchdog activeFailed（隔离失败） | 接收面同 close 语义；监听器闭包经票 07 `isContextDead` 短路 | 同左 | 保留 | 票 07 机制；本域不自持 Context | 票 17 `isolatedActiveFailureDropsInFlightPackets`（同构面）；本域 close 用例覆盖同型正交性 |
| commit 边界（commit 前包由 active 服务） | 不适用（接收面无 generation 概念——包到达即写 mirror） | 同左 | — | 数据域接收不消费 generation | `receiveFaceFeedsMirrorsIndependentOfGenerations` |
| 断线 / 离开旧世界 / 切维度 | `clearClientMirrors()`（平台钩子独占） | `ClientDataStore.SHARED.clear()`（同） | 不适用（服务端域） | 域钩子持有清空权 | `PDataSyncAcceptTest.clearClientMirrorsWipesEverything`、fabric `ClientLevelWatchTest`（首次进服 null→世界不清）、`ClientDataStoreTest.sameKeyOverwritesAndClearResets` |
| 首次进服 | 不清（fabric `ClientLevelWatch` null→世界 = false；NeoForge 无 unload 事件天然不触发） | 同左 | — | 守卫状态机 | `ClientLevelWatchTest.firstJoinDoesNotReportLeftPreviousLevel` |

服务端接收面（脚本自定义通道）与 owner 线程/generation 的完整语义表见票 17
`2026-09-15-network-sync/evidence/stale-generation-semantics.md`——本域不重复，只补上表的数据域投影。
