# 脚本包阶段 A 首批验证

日期：2026-10-14。基点：`fe89c770d2d47efe1d3f72421328565eaf7dd889` 加本地未提交变更。范围见 [实施记录](2026-10-14-script-pack-implementation.md)。这不是 B–E 或整体包任务验收。

## 最终检查

```powershell
.\gradlew.bat :common:check guardLint :1.21.1:build :26.1.2:build :26.2.0:build :26.1.2-fabric:build :26.2.0-fabric:build --console=plain
git diff --check
```

PASS：最终联合检查 `BUILD SUCCESSFUL in 1m 31s`，104 项任务（51 executed / 53 up-to-date）。`checkCommonIsolation`、addon fixture 隔离、guardLint 和五节点 artifact/source-set 检查通过。`git diff --check` 通过。原始联合输出在本地 `build/script-pack-phase-a-verification.log`；构建目录日志不承诺进入提交。

| 测试集 | 总计 | 失败 | 错误 | 跳过 |
|---|---:|---:|---:|---:|
| `common` | 2267 | 0 | 0 | 4 |
| `versions/1.21.1` | 433 | 0 | 0 | 31 |
| `versions/26.1.2` | 668 | 0 | 0 | 81 |
| `versions/26.2.0` | 668 | 0 | 0 | 81 |
| `versions/26.1.2-fabric` | 390 | 0 | 0 | 37 |
| `versions/26.2.0-fabric` | 390 | 0 | 0 | 37 |

7 项新增包操作回归全部执行并通过：跨来源同名拒绝/限定选择；唯一裸 ID 与未找到；保存失败；损坏状态保护；原子替换和暂存清理；远端查询/只读/不重扫；当前世界刷新与退出后不复活。

扩展的 `WorldPackRelativePathExecutionTest` 实际执行 Graal：WORLD 初次执行 → 保存禁用意图（旧运行结果保留）→ 计划不执行 → 当前世界 reload 不再执行 → 重新启用/reload 执行 → 坏候选失败（健康旧结果保留）→ 单纯发现不更新运行事实 → 世界监听清理移除运行结果。该回归未跳过。

命令树测试补充了无需引号的 `WORLD:demo` 解析断言。部分节点的裸 JVM 命令树初始化原有条件导致测试跳过；不能将源码接线检查当成实际客户端命令同步通过。跳过数完整列出，不记成全部测试执行。

## 修复过程中的失败

FAIL（已修复）：加入 status/plan 查询后，五个节点的 `RuntimeCommandLifecycleSourceTraceTest` 拒绝命令对 `root.scriptManagerOrNull` 的访问。改为 root-owned `hasScriptType` 查询后，保持原门禁断言，最终五节点测试通过。没有删除、弱化或排除失败回归。

## 源码与制品绑定

以下清单覆盖此次修改的生产代码、资源和测试（排除 wiki 文案及两份既有 golden 差异）。清单 UTF-8/LF SHA-256：`68954f73c76275ad6f3dd8a587fad212b189b10496ab6365e6e2f4af1e5bf14d`。

```text
38092658e008d5f561d29a4d2eb2981d42d4eee038d40124a20a9ce6f8b5303d  common/src/main/java/com/tkisor/nekojs/core/ScriptLocator.java
16c2ec42e0f156687e9002b1d2dec9f21259f2e8b1bada9d5e8d40c2c7ffbaef  common/src/main/java/com/tkisor/nekojs/core/lifecycle/NekoRuntimeRoot.java
1393c2959246bc085cd799103f5ae0837de28c94f04ef093ea09547e9cda4bc4  common/src/main/java/com/tkisor/nekojs/core/pack/PackExecutionPlan.java
47d9e1e1eb27c30c1c477d3b0a3040a692732f2ec8ad4cdd7ba0f5d797e82f61  common/src/main/java/com/tkisor/nekojs/core/pack/PackOperationResult.java
0d6b000b89dc23682852ae7f0be23b7c5dd32b184d021e02a1d9088b6a440ec4  common/src/main/java/com/tkisor/nekojs/core/pack/PackRuntimeView.java
95ff9501496a6ad2b8ae2007b2c8fe2303483666ef28ee422bc751b2b864d6bb  common/src/main/java/com/tkisor/nekojs/core/pack/PackScriptView.java
a67a80b609128ea89d89abc0102cf4a7c56209bd69aa0433ab7b88795bf0fb7c  common/src/main/java/com/tkisor/nekojs/core/pack/ScriptPack.java
47dafd2f833c90931e142ec8d21fe5138afa58481d079b74f2ac19862ffe2390  common/src/main/java/com/tkisor/nekojs/core/pack/ScriptPackRegistry.java
28b4e8c9d692e6e69e19576dc40c4a96053a780c1b9daf30a9ac6d20f1a66cfe  common/src/main/java/com/tkisor/nekojs/core/pack/ScriptPackState.java
5432b6ce15dfe0544015ce4aad2adfc693ed755eb07009da6a80507d541d7507  common/src/main/java/com/tkisor/nekojs/script/ScriptContainer.java
1e16d08336dd015db8fa5f628d29fa82a23f36d21384e8a82b844f75cfb155e0  common/src/main/java/com/tkisor/nekojs/script/ScriptManager.java
aa050288a1c5c46b5527d6980af98c40686b20faef874c6ffe9afdebea7c3bbd  common/src/main/resources/assets/nekojs/lang/en_us.json
7d6a42b8b062519112bfb6d23564cd1427f1e88b1e148291c576045c8a5a62b5  common/src/main/resources/assets/nekojs/lang/zh_cn.json
29b5059feb7640c5d6fbcfef124f989f84b1db2e95f047864f121bd68f7e05f6  common/src/test/java/com/tkisor/nekojs/core/pack/ScriptPackOperationsTest.java
da84310aecbbca39684c718fe3c4147ff109fd793295bfbf048d50852b359ce9  common/src/test/java/com/tkisor/nekojs/script/WorldPackRelativePathExecutionTest.java
6c77e7db4faa7b2a067d4d5547dcd89c0c34d6c0b63eb50b602b1f8c23fe1cf0  src/fabric/java/com/tkisor/nekojs/fabric/FabricNekoJSCommands.java
b3657fe6166d2c59faac3caa74c73c007427fc7c5b74027ee78869a0490d1f64  src/main/java/com/tkisor/nekojs/command/NekoJSCommands.java
976143b5e390df5b7382e46097118d31c458182ff03eda890b8a3ddad6df866c  src/test/java/com/tkisor/nekojs/command/NekoJSCommandsTrustArgumentAndUsageTest.java
574500c201336034e5b81d3235622390fc49e7aa570390243065b5977d34c605  src/test/java/com/tkisor/nekojs/pack/FabricWorldPackStatusTest.java
ae0512f757360ae7dccfbd5d9f63c4e741695215b57092eb18fec018eacd4e6c  versions/1.21.1/src/main/java/com/tkisor/nekojs/command/NekoJSCommands.java
```

这是 NekoJS 本体的节点构建产物；脚本包模组 JAR 生成器尚未实现。

| 节点 | 制品 | SHA-256 |
|---|---|---|
| `1.21.1` | `nekojs-neoforge-1.21.1-1.1.0-preview3-server.jar` | `9d2edd2adb9094c808198df3c33a6e74a04cf12fff4973ae87ca99b7af0319c2` |
| `1.21.1` | `nekojs-neoforge-1.21.1-1.1.0-preview3.jar` | `f214370c73f2cfc51a125c185bf0727ef5f053ee38546a824da779fd58a3a631` |
| `26.1.2` | `nekojs-neoforge-26.1.2-1.1.0-preview3.jar` | `6812d39d5df1d9f21aed53d2a871ec201238d839b6032c0611ac99c89815b862` |
| `26.2.0` | `nekojs-neoforge-26.2.0-1.1.0-preview3.jar` | `cc5c38804b546fde12cc1bc6355680351791538b56da8be13e24bbfa8be6fa4d` |
| `26.1.2-fabric` | `nekojs-fabric-26.1.2-1.1.0-preview3.jar` | `2d6ca303f17df7ff44a7e42a60bd0cf20d3712fd8d34d70a5dfde9375d064ac5` |
| `26.2.0-fabric` | `nekojs-fabric-26.2-1.1.0-preview3.jar` | `5348d530ac157af511fc511e866b2ca8259a89fee99493c50fd9e8da710f9400` |

## 未运行与剩余工作

NOT_RUN：新版制品的真实 Minecraft 客户端/专用服务器命令验收、跨进程远端同步运行验收、GameTest、独立 JDK 21/平台矩阵。当前只证明引擎执行与节点构建/测试，不证明实际 MC 资源卸载或联网启停。维护者验收：未记录实际结论，未代签。

A 后续：刷新、网络、世界与运行请求进一步集中为 root-owned 包操作；完整包级状态/失败聚合和当前客户端远端包查询的入口体验。B–E（严格新清单、依赖、包实例/资源、动态能力、局部候选集合、创建/归档/安装、生成并发现模组 JAR）仍未实现。

旧 Script API / Plugin API、文件入口扫描与 Context/global、签名/hash、状态文件字段和 wire 没有迁移。新增命令是兼容扩展；同名裸 ID 从误选首个来源改为报歧义，损坏状态从覆盖改为拒绝保存，WORLD 意图从重新进入世界生效改为对应类型 reload 生效。这些行为变化已同步作者文档。
