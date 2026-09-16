# 2026-09-16 data sync：PData/ClientData 语义钉住与 generation 边界报告（ticket 18）

> 工单：`docs/architecture-refactor/implementation-tickets/18-data-sync.md`（票面 Status/AC 勾选未动，关票由主会话负责）。
> 分支：`ticket-18-data-sync`（worktree `D:\mcmodDemo\NekoJS\.worktrees\t18\NekoJS-mult`），基线 `9da3abf3`（认领提交）。
> 核心 spec：`05-runtime-lifecycle-and-data.md`（user story 16/17/24/25：pdata key/跨 loader 语义、wire 默认不变）、`09-reload-candidate-state-and-thread-contract.md`（generation 边界、close/watchdog）。
> 前置交付（消费不推翻）：票 03 数据盘点与缺口登记、票 17 wire golden/stale-generation 语义表/trace 先例。
> 证据：本目录 `evidence/`（`pdata-sync-semantics.md`、`generation-boundary-semantics.md`、`save-format-fixtures.md`、`verification-commands.md`）。

**姿态（工单原文）**：不改存档格式/key/wire/跨 loader 语义，把现行为钉进 fixture 并闭合 generation 边界。
唯一的生产行为改动是**修复票 03 §3-3 登记的 joinLevel 窗口写 pdata 静默丢弃缺口**（工单点名归本票：「能修则修」）。

---

## 1. 结论摘要

1. **joinLevel 缺口已修复**：pdata 写入路径从「按实体 id 反查 level 实体索引」改为「调用现场直接解引用实体持久化容器」——`EntityPDataStore.Access` 新增实体引用面（default 委托 id 面），NeoForge/fabric 两平台 install override 为直接解引用（`getPersistentData()` / `NekoEntityPData` duck 根），`EntityExtension#neko$pdata` 的静态入口改走实体引用面。`EntityJoinLevelEvent` 窗口内的脚本写入不再静默丢失。存档 key、NBT 形状、wire、跨 loader 语义零变化。
2. **行为面全部 fixture 化**：存档兼容（fabric mixin 真实方法体 ×2 节点 + literal 旧 NeoForge 存档回读）、dirty 写/读语义、ClientData JSON 类型/32768 上限/覆盖/坏包丢弃（双 loader 接收面）、清空时机与首次进服守卫（fabric 状态机提取为 `ClientLevelWatch` 并 JVM 钉住）、generation 边界（真实 ScriptManager+Graal harness 七用例）。wire hex 引用票 17 golden，不重复造。
3. **generation 边界是结构性满足**：PData mirror 与 ClientData store 是进程级 Java 状态，接收面从不进入 Graal Context——reload/candidate 失败/close 不触碰数据面，清空权只属于平台域钩子（断线/离开旧世界）。语义表见 `evidence/generation-boundary-semantics.md`。

## 2. 变更清单（按模块）

| 文件 | 类型 | 内容 |
|---|---|---|
| `src/main/java/com/tkisor/nekojs/api/inject/EntityPDataStore.java` | 修改 | `Access` 增加实体引用面 default 方法（委托 id 面）；`getPDataTag/setPDataTag` 改走实体引用面；javadoc 更新（joinLevel 动机） |
| `src/main/java/com/tkisor/nekojs/NekoJSMod.java` | 修改 | NeoForge install override 实体引用面：直接 `entity.getPersistentData()`（不再经 `pdataContainer` id 反查；id 面保留反查） |
| `src/fabric/java/com/tkisor/nekojs/fabric/FabricPDataSync.java` | 修改 | fabric install override 实体引用面：直接 `((NekoEntityPData) entity).neko$getPDataRoot()`；registerClient 的「离开旧世界才清」改用 `ClientLevelWatch` |
| `src/fabric/java/com/tkisor/nekojs/fabric/FabricPlayNetwork.java` | 修改 | registerClient 的 ClientData 清空守卫改用 `ClientLevelWatch`（行为等价提取） |
| `src/fabric/java/com/tkisor/nekojs/fabric/ClientLevelWatch.java` | 新增 | 「离开旧世界才清」状态机提取（fabric 侧两域共享；JVM 可测） |
| `src/test/java/com/tkisor/nekojs/api/inject/PDataJoinWindowResolutionTraceTest.java` | 新增 | joinLevel 修复源码 trace（4 用例，五节点共跑） |
| `src/test/java/com/tkisor/nekojs/wrapper/DataSyncGenerationBoundaryTest.java` | 新增 | 真实 ScriptManager harness 上的 generation 边界/保留/reload 不回滚副作用（7 用例，五节点共跑，含 1.21.1 守卫） |
| `src/test/java/com/tkisor/nekojs/wrapper/clientdata/ClientDataSyncDomainTest.java` | 新增 | ClientData 发送面域语义（6 用例，五节点共跑） |
| `src/test/java/com/tkisor/nekojs/wrapper/clientdata/ClientDataReceiveHandlerTest.java` | 新增 | NeoForge 接收面 hop/坏包丢弃（3 用例，`//? if neoforge` 守卫） |
| `src/test/java/com/tkisor/nekojs/wrapper/pdata/PersistentDataJSTest.java` | 扩展 | 新增 dirty/syncer 契约用例（写触发/读不触发/sync 分离） |
| `versions/26.1.2-fabric`（+26.2.0-fabric 同体一份）`src/test/.../ClientLevelWatchTest.java` | 新增 | 首次进服/切维度/断线守卫状态机（3 用例 ×2 节点） |
| `versions/26.1.2-fabric`（+同体一份）`.../FabricClientDataAcceptTest.java` | 新增 | fabric 接收面坏包丢弃/覆盖/JSON null（3 用例 ×2 节点） |
| `versions/26.1.2-fabric`（+同体一份）`.../mixin/NekoEntityPDataSaveShapeTest.java` | 新增 | 存档兼容 fixture：生产 mixin 真实 save/load 方法体 + literal 旧 NeoForge 存档回读（7 用例 ×2 节点） |

**wire / payload / 存档 key / NBT 形状：零改动。**

## 3. joinLevel 缺口处置结论（单列）

- **现状核实（07 轮后）**：缺口仍在——`EntityPDataStore` 两平台实现按 id 反查（`NekoJSMod.pdataContainer` / `FabricPDataSync.findEntity` 遍历 `level.getEntity(id)`），`EntityJoinLevelEvent` 窗口内实体未入索引 → 写静默 no-op。与 03 REPORT §3-3 描述一致，未被 06/07 轮修复。
- **处置**：已修（§2 前三行）。修复方向即 03 建议的强形式：不引入延迟提交或 WARN 补偿，而是让写入根本不依赖索引（调用现场本来就持有实体引用）。
- **行为变化**：join 窗口内的 pdata 写从「静默丢弃」变为「落盘生效（随实体存档）」。这是修 bug：不存在依赖「写被丢弃」的合理脚本场景；存档格式/key/wire 不变，旧存档读写不受影响（fixture 1/2/6 证明）。
- **残留 characterization**：真实 `EntityJoinLevelEvent` 事件内的端到端写读（真机）未跑——裸 JVM 无 FML 无法构造实体/level。结构性证据 = trace（实体引用面无反查调用）+ 平台 API 语义（`getPersistentData()`/mixin 字段与索引成员资格无关）。建议主会话用 minecraft-mcp 起客户端补一轮 in-game smoke（口径同票 17 R2）。

## 4. AC 逐条判定

| # | AC | 判定 | 证据 |
|---|---|---|---|
| 1 | 旧 NeoForge 与 Fabric 存档 fixture 中的 NeoForgeData/NekoJSPersistentData 均可回读，PData key、NBT 形状和跨 loader 语义不变 | **满足** | `evidence/save-format-fixtures.md`：literal 旧 NeoForge 存档（含 vanilla 字段 + 其它 mod 键）经 fabric 生产 mixin load 回读（`oldNeoForgeSaveReadsBackOnFabric` 等 7 用例 ×2 fabric 节点）；写出形状 `NeoForgeData→NekoJSPersistentData` 与 NeoForge patched Entity 同形；NeoForge 读路径语义（copy/空移除）由既有 `EntityPDataStoreTest` + trace 钉住。真机存档目录回读引用 03 票实测（characterization） |
| 2 | PData 写入触发 dirty，读不触发；flush 保持每 tick 上限、tracking player 目标和 revision 递增语义 | **满足（JVM 面 + 语义表实读；flush 循环为 characterization）** | dirty 写/读/sync 分离：`PersistentDataJSTest.writesFireDirtyMarkerButReadsAndExplicitSyncDoNot`；flush 256/tick、tracking 目标、revision merge 递增、32768 超限跳过的语义表（实读锚点）：`evidence/pdata-sync-semantics.md` §2/§3——真实 Entity/tick 无法裸 JVM 驱动服务端发送循环，行为锚点为本票未改的既有代码 + 03 真机场景 1/2 + 票 17 dispatcher 装配测试 |
| 3 | 空数据包清除对应 entity mirror，stale revision 被拒绝，entity id 复用后不读旧实体数据 | **满足** | `PDataSyncAcceptTest`（空包清除/同 revision 幂等/stale 拒绝/clearClientMirrors）+ `DataSyncGenerationBoundaryTest.receiveFaceFeedsMirrorsIndependentOfGenerations`（stale 拒绝与 generation 无关）；id 复用防线（onEntityRemoved 空包 + 服务端账清）语义表 `evidence/pdata-sync-semantics.md` §3 |
| 4 | ClientData 仅接受旧契约 JSON 类型，超限值显式失败，同 key 覆盖，坏包丢弃并记录警告 | **满足（WARN 记录面为 characterization）** | `ClientDataSyncDomainTest`（类型拒绝显式抛且零发送/32768 边界（恰 32768 过、超限抛）/覆盖/key 校验）；`ClientDataReceiveHandlerTest`（NeoForge hop 后坏 JSON 丢弃不抛不写、后续包不受影响）+ fabric `FabricClientDataAcceptTest` 同语义；「记警告」的日志文本不做 appender 断言，如实记为 characterization |
| 5 | ClientData 在断线、离开旧世界/切维度按现约清空；首次进服数据不被进入世界钩子误删 | **满足** | fabric：`ClientLevelWatchTest`（null→世界不清、世界→世界清、世界→null 清、重进服不清、两域守卫独立）——状态机从生产代码提取为 `ClientLevelWatch`，测试打的是生产类；NeoForge：挂在 `ClientLevelEvent.Unload`（断线/切维度都触发；首次进服无 unload 事件，天然不误删），实读 + `ClientDataStoreTest.sameKeyOverwritesAndClearResets` |
| 6 | PDataSyncPacket 与 ClientDataSyncPacket 的 id、方向、codec、字段顺序和 JSON/NBT wire 与旧 fixture 一致 | **满足（引用票 17，零改动）** | 票 17 `PayloadWireFormatGoldenTest` 已含两 payload 的 id 断言 + encode golden hex + 等价解码（五节点同 hex）；方向由票 17 `NetworkRegistrationSourceTraceTest` 双 loader 方向钉住。本票零 wire 改动，未重复造 fixture |
| 7 | SERVER/CLIENT reload、candidate 失败和 root close 后，同步回调不进入旧 Context，持久化数据与客户端 mirror 的保留/清空规则符合各域契约 | **满足** | `evidence/generation-boundary-semantics.md` 全表 + `DataSyncGenerationBoundaryTest` 7 用例（SERVER/CLIENT reload 保留与脚本读取对照、candidate 失败保留、close 后接收面安全 + 已关闭 Context 反证、reload 不回滚实体 pdata、接收面独立于 generation）；「不进入旧 Context」在本域结构性满足（接收面无 Context），脚本自定义通道的对应面引用票 17 |
| 8 | 若旧 fixture 发现格式必须修复，先补迁移门再删旧读路 | **满足（前置未触发）** | 全部旧 fixture（存档 literal、wire hex、JSON 契约）在零格式改动下回读/解码一致——无格式修复需求，AC8 的「若必须修」分支未触发；未删任何读路径（id 面/旧读路全保留） |

## 5. 与 03 / 17 的衔接

- **03（data-protection）**：§3-3 joinLevel 缺口由本票修复（§3）；03 的 pdata/pack/config 盘点数据面本票零改动（fixture 3/4 证明存档键形状不变）；03 §3-2 指出的「迁移设施缺位」在 AC8 未触发路径下无需面对；03 not-verified #3（PData/ClientData wire 多端实际收发）的 JVM 可达半面已由 17+18 覆盖，真机半面仍留 in-game smoke。
- **17（network-sync）**：wire golden、payload 注册面、stale-generation 语义表、trace/defense 测试模式被本票直接消费；本票不重复网络域 fixture，只补数据域语义。`TestGameDirs.unique` 的 gameDir 纪律在本票 harness 遵守。

## 6. 已知缺口 / 风险与偏离说明（给 reviewer）

| # | 项 | 说明 | 处置建议 |
|---|---|---|---|
| R1 | joinLevel 修复是生产行为变化 | join 窗口写从静默丢弃 → 落盘生效。见 §3；语义上无依赖旧缺陷的合理场景 | reviewer 重点看 `EntityPDataStore`/`NekoJSMod`/`FabricPDataSync` 三处 diff（各 <20 行） |
| R2 | flush 循环/服务端 revision 递增/超限 WARN 未 JVM 化 | 真实 Entity/tick 依赖（裸 JVM 无 FML）。语义以实读表 + 03 真机 + 零改动事实钉住 | 若要求运行时证据，主会话用 minecraft-mcp 补 in-game smoke |
| R3 | id 面 Access 方法（id 反查）保留但生产无调用者 | `EntityExtension` 全走实体引用面后，`pdataContainer`/`findEntity` 只剩接口契约与测试消费。保留理由：Access 的 id 面是「同步/镜像层按 id 记账」的接口锚点（`EntityPDataStoreTest` 契约），删接口面属 API 破坏 | 后续票据若收口可评估降级/删除 |
| R4 | 同 revision 重发接受（== 不拒绝）保留为现状 | `acceptClientSync` 对 `revision == current` 接受（幂等覆盖）——旧契约如此，本票按「现行为钉住」保留并在 fixture 明示 | 若视为缺陷另开票（wire/服务端递增不变） |
| R5 | WARN 日志文本未断言 | 坏包丢弃/超限跳过的日志面以代码实读为准（log4j appender 断言成本 > 价值） | characterization |
| R6 | in-game smoke 未跑 | 与票 17 R2 同口径：平台 event 单次触发、真实存档目录回读、真机 joinLevel 写读未起真机 | 主会话 minecraft-mcp 一轮可全部补掉 |
| R7 | `ClientLevelWatch` 提取自 `FabricPlayNetwork`/`FabricPDataSync` 的内联 lambda | 行为等价提取（引用比较/仅离开旧世界清），生产 diff 各 ~6 行；「等价」由两域各自 fixture + 新状态机测试背书 | — |
| R8 | fabric 节点本地测试双份同体分发 | `FabricVersionCompatWiringTest`/`FabricNetworkRegistrationOnceTest` 先例（`//? if fabric` 守卫对 active 节点是惰性注释，不能放共享树）；26.1/26.2 API 实测同形（TagValueInput/Output、ProblemReporter.DISCARDING） | 若 26.2 API 漂移，节点本地各自适配 |

## 7. 迁移影响

无。存档格式、pdata key（`NekoJSPersistentData`）、容器键（`NeoForgeData`）、wire id/codec/字段顺序、
ClientData JSON 契约与 32768 上限全部不变（fixture 证明）。唯一行为变化是 joinLevel 窗口写入生效
（§3），无数据迁移、无公开接口变化、无配置变化。

## 8. 验证（全绿；命令与逐类计数见 `evidence/verification-commands.md`）

| 命令 | 结果 |
|---|---|
| `./gradlew :common:check :common-api-processor:test --console=plain` | 绿 |
| `./gradlew guardLint --console=plain` | 绿 |
| `./gradlew :26.1.2:build --console=plain` | 绿（test 271/0 fail/36 skipped） |
| `./gradlew :26.1.2-fabric:build --console=plain` | 绿（test 174/0 fail/8 skipped） |
| 新 fixture 五节点专项（26.2.0/1.21.1/26.2.0-fabric） | 全绿（逐类计数见 evidence §2） |

五节点全量 build 由主会话合并后统一跑（工单约定）。
