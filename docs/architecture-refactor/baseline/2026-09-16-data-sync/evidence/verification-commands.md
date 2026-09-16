# 票 18：验证命令与结果

worktree：`D:/mcmodDemo/NekoJS/.worktrees/t18/NekoJS-mult`，分支 `ticket-18-data-sync`（基线 9da3abf3）。
执行日：2026-09-16。五节点全量 build 由主会话合并后统一跑（工单约定）。

## 1. 工单四命令（全绿）

| 命令 | 结果 |
|---|---|
| `./gradlew :common:check :common-api-processor:test --console=plain` | BUILD SUCCESSFUL（20 tasks） |
| `./gradlew guardLint --console=plain` | BUILD SUCCESSFUL（10 tasks；守卫块/扫描文件数与票 17 后基线一致，0 豁免 0 警告输出） |
| `./gradlew :26.1.2:build --console=plain` | BUILD SUCCESSFUL（26 tasks；test 全量 **271 tests / 0 failures / 0 errors / 36 skipped**） |
| `./gradlew :26.1.2-fabric:build --console=plain` | BUILD SUCCESSFUL（27 tasks；test 全量 **174 tests / 0 failures / 0 errors / 8 skipped**） |

## 2. 新增/扩展 fixture 的五节点运行（专项过滤）

命令形如：
`./gradlew :<node>:test --tests "com.tkisor.nekojs.api.inject.*" --tests "com.tkisor.nekojs.wrapper.pdata.*" --tests "com.tkisor.nekojs.wrapper.clientdata.*" --tests "com.tkisor.nekojs.wrapper.DataSyncGenerationBoundaryTest" [--tests "com.tkisor.nekojs.fabric.ClientLevelWatchTest" ...]`
（26.2.0 / 1.21.1 / 26.2.0-fabric 同式）——五节点全部 BUILD SUCCESSFUL，逐类计数：

| 测试类（新增◆/扩展◇） | 26.1.2 | 26.2.0 | 1.21.1 | 26.1.2-fabric | 26.2.0-fabric |
|---|---|---|---|---|---|
| ◆ PDataJoinWindowResolutionTraceTest | 4/0 | 4/0 | 4/0 | 4/0 | 4/0 |
| ◆ DataSyncGenerationBoundaryTest | 7/0 | 7/0 | 7/0 | 7/0 | 7/0 |
| ◆ ClientDataSyncDomainTest | 6/0 | 6/0 | 6/0 | 6/0 | 6/0 |
| ◆ ClientDataReceiveHandlerTest（neoforge 守卫） | 3/0 | 3/0 | 3/0 | — | — |
| ◇ PersistentDataJSTest | 3/0 | 3/0 | 3/0 | 3/0 | 3/0 |
| ◇ PDataSyncAcceptTest（既有，>=26 守卫） | 4/0 | 4/0 | — | 4/0 | 4/0 |
| ◆ ClientLevelWatchTest（fabric 节点本地） | — | — | — | 3/0 | 3/0 |
| ◆ FabricClientDataAcceptTest（fabric 节点本地） | — | — | — | 3/0 | 3/0 |
| ◆ NekoEntityPDataSaveShapeTest（fabric 节点本地） | — | — | — | 7/0 | 7/0 |

（格式 tests/failures；fabric 节点本地测试在两 fabric 节点各一份同体分发。）

## 3. 复跑记录

- 首轮共享树 26.1.2 有 4 处 fixture 自身缺陷（Map.of 无序、candidate 失败模式选错为「per-script 容错」、
  trace needle 尾括号、Long/Integer 装箱）——修正后复跑全绿；fabric 首轮反射漏 CallbackInfo 参数
  （@Inject 处理器签名）——修正后两 fabric 节点全绿；1.21.1 首轮两处 26 API（getCompound Optional/
  getIntOr）——加 `//? if >=26` 守卫后全绿。修正过程不改变任何生产代码。
