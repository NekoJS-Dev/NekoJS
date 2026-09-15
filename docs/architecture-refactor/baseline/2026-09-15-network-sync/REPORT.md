# 2026-09-15 network sync：注册一次、wire 不变与脚本通道 owner 调度报告（ticket 17）

> 工单：`docs/architecture-refactor/implementation-tickets/17-network-sync.md`（票面 Status/AC 勾选未动，关票由主会话负责）。
> 分支：`ticket-17-network-sync`（worktree `D:\mcmodDemo\NekoJS\.worktrees\t17\NekoJS-mult`），基线 `eba89230`（= master 顶，认领提交）。
> 核心 spec：[`../../specs/05-runtime-lifecycle-and-data.md`](../../specs/05-runtime-lifecycle-and-data.md)（user story 4/25：reload 不重复注册 network、wire id/格式默认不变）、[`../../specs/09-reload-candidate-state-and-thread-contract.md`](../../specs/09-reload-candidate-state-and-thread-contract.md)（commit 边界/owner 线程/watchdog 隔离）。
> 前置交付（消费不推翻）：票 06 candidate/commit（`2026-09-12-reload-candidate`）、票 07 gate/watchdog/close 抢占/activeFailed（`2026-09-15-runtime-threads`）。
> 证据：本目录 `evidence/`（`golden-capture-eba89230-26.1.2.txt`、`verification-commands.md`、`stale-generation-semantics.md`）。

**范围边界（工单明文，全程遵守）**：不补 PData/ClientData payload 域的 parity、不做 pack trust 语义（配置期桥复用，信任语义归 PACK_TRUST）、不补 Fabric 编辑器/dashboard/客户端显示网络面、不做性能项。

---

## 1. 结论摘要

1. **生产代码几乎零改动是结论而不是缺工**：对 AC 逐条核对后，注册时机（loader 原生 event/entrypoint）、接收侧 hop→owner→generation 路由、stale packet 边界、防线行为在基线已由票 05/06/07 的机制满足——本票的增量是把这些行为**钉进可回归的 fixture**（wire golden、注册一次、路由/generation/stale、防线等价），外加一处过时 javadoc 修正（§2 表末行）。唯一的行为性改动是测试基建（per-JVM gameDir），主源码行为零变化。
2. **wire fixture 五节点闭合**：6 个 payload（script_payload/pdata_sync/client_data_sync/show_error_list/pack_hashes/pack_bundle）以 eba89230 实测 golden hex 钉住 encode 方向，以等价解码对照钉住 decode 方向，id 逐一断言；同一组 hex 在 26.1.2 / 26.2.0 / 1.21.1 / 两 fabric 节点全部通过（跨版本线格式一致）。
3. **注册一次**：NeoForge 经 `RegisterPayloadHandlersEvent` 订阅、Fabric 经 entrypoint 各恰一处 dispatcher 装配（全仓 main 源恰 2 处 `PlayPacketDispatchers.install`）；reload/命令面文件零 payload 注册调用（源码 trace 钉住）；fabric-api 对重复注册以 IllegalArgumentException 拒绝（node-local JVM fixture 钉住）；compat seam 恰注册一个 bidirectional script payload。
4. **generation/stale 语义表**（含机制与 fixture 映射）见 `evidence/stale-generation-semantics.md`：commit 前不进 candidate、commit 后只由新代处理一次、watchdog 隔离/close 后丢弃且不触碰已关闭资源，全部有确定性 JVM fixture。

## 2. 变更清单（按模块）

| 文件 | 类型 | 内容 |
|---|---|---|
| `src/test/java/com/tkisor/nekojs/network/PayloadWireFormatGoldenTest.java` | 新增 | AC2 wire fixture：6 payload 的 id/encode-hex/decode-roundtrip 三面钉住（5 节点共跑，13 用例） |
| `src/test/java/com/tkisor/nekojs/network/NetworkGenerationRoutingTest.java` | 新增 | AC3/AC4/AC5 fixture：真实 ScriptManager+Graal 管线上的 owner 路由、candidate/commit 边界、close/watchdog 隔离后的 stale packet 丢弃、监听器异常遏制（8 用例） |
| `src/test/java/com/tkisor/nekojs/network/NetworkPayloadDefenseLineTest.java` | 新增 | AC6 防线等价：非法 channel/坏 NBT/超大 payload/空 key 的解码拒绝 + NetworkJS 发送面语义（9 用例） |
| `src/test/java/com/tkisor/nekojs/network/NetworkRegistrationSourceTraceTest.java` | 新增 | AC1/AC2(方向)/AC7 源码 trace：注册只在 loader 原生入口、dispatcher 装配恰两处、reload 面零注册、fabric 显式子集 + 双 loader 方向钉住（9 用例） |
| `src/test/java/com/tkisor/nekojs/platform/compat/ScriptPayloadRegistrationShapeTest.java` | 新增 | AC1/AC3 compat seam（26.x NeoForge）：恰一个 bidirectional script_payload 注册 + 两 handler 经 enqueueWork hop 进 owner 总线（2 用例；`//? if neoforge { //? if >=26` 守卫） |
| `versions/26.1.2-fabric/src/test/java/com/tkisor/nekojs/fabric/FabricNetworkRegistrationOnceTest.java`（26.2.0-fabric 同体一份） | 新增 | AC1 fabric 侧：首次 registerServer 成功、重复注册被平台以 IAE 拒绝（节点本地分发） |
| `src/fabric/java/com/tkisor/nekojs/fabric/FabricPlayNetwork.java` | 修改（仅 javadoc） | 过时注释修正：类 javadoc 曾称「NekoScriptPayload 自定义通道尚未接」——与实现相悖（2026-09-02 第二批已接线，`docs/fabric-port-status.md` 有记录）；改为如实的显式子集陈述，并显式列出刻意缺失面（ShowErrorListPacket/编辑器/dashboard），即 AC7 的文档面 |
| 审查整改（7 findings，见 §9） | 修改 | 证据勘误（trace 终版五节点补跑）、hop 断言强化、坏 NBT 断言下界、fabric once 测试 @AfterEach 恢复、trace 扫描纳入 common、重复 close 用例行为化、固定名 gameDir 全量唯一化（`TestGameDirs`，flake 根治） |

**主源码行为改动：零。** 唯一触及的主源文件是 FabricPlayNetwork 的 javadoc（不进字节码语义）。

## 3. 与票 06/07 机制的衔接（消费，不重造）

- **owner 调度**：网络接收侧从不直接进 Context——平台 hop（enqueueWork / server|client().execute()）落主线程（= owner 线程）后才进 `NetworkMessageHandler` 中立投递 → `NetworkEvents` 总线 → 监听器闭包（闭包内有票 07 的 `noteCallbackEnter/Exit` 与 `isContextDead` 短路）。本票零新增调度面。
- **generation 边界**：候选期网络监听器走票 06 pendingListeners（不挂总线）；commit 清扫→发布→激活的顺序使「commit 前包由 active 服务、commit 后只由新代接收一次」成立；close 的 fullReloadCleanup 清类型监听器；activeFailed 的分发短路由 isContextDead 承担。网络层不自持 Context/timer/binding，因此 stale 丢弃不需要网络侧撤销资源。
- **防线**：解码边界的校验（channel 长度、key 非空、pack 上限、字符串上限）与 post 核心吞监听器异常均为既有行为，本票只做 fixture 化。

## 4. AC 逐条判定

| # | AC | 判定 | 证据 |
|---|---|---|---|
| 1 | NeoForge 与 Fabric 的 payload 注册在启动/客户端初始化各发生一次；多次 reload 后注册计数与平台连接协商不变 | **满足（JVM 面 + 源码 trace；平台 event 单次触发为 loader 契约，见 characterization）** | `ScriptPayloadRegistrationShapeTest`（compat seam 恰一注册）、`NetworkRegistrationSourceTraceTest`（`dispatcherInstallAppearsExactlyOncePerLoaderInMainSources` 全仓恰 2 处；`reloadAndCommandPathsContainNoPayloadRegistration` 命令/root/manager 零注册调用；fabric entrypoint 恰一次）、`FabricNetworkRegistrationOnceTest`×2 节点（平台拒绝重复注册） |
| 2 | payload 保持旧 id、方向、codec、字段顺序和线格式；新旧 fixture 字节或等价解码对照一致 | **满足** | `PayloadWireFormatGoldenTest`（13 用例：id 断言 + 6 encode-hex 逐字节 + 6 decode-roundtrip 完全消费断言；golden 来自 eba89230 实测，`evidence/golden-capture-eba89230-26.1.2.txt`）；方向由 `neoforgePayloadDirectionsStayOnLegacyRegistration` / `fabricPayloadDirectionsStayOnLegacyRegistration` 钉住；字段顺序在 hex 可读投影（测试 javadoc）与 decode 字段断言双重钉住 |
| 3 | Network.sendToServer/sendToPlayer/sendToAll 语义不变；接收事件在平台主线程/owner 队列执行 | **满足（JVM 面 + hop 源码 trace；真机 sendToServer 见 characterization）** | `NetworkPayloadDefenseLineTest.networkSendRoutesThroughInstalledDispatcherWithLegacyPayloadShape`（发送面经 dispatcher、null→空 tag 归一化）；`ScriptPayloadRegistrationShapeTest.registeredHandlersHopMainThreadThenRouteIntoOwnerBus`（enqueueWork 模型）；`fabricScriptChannelReceiversHopMainThreadBeforeNeutralDispatch`（fabric 两 receiver 都先 hop）；`NetworkGenerationRoutingTest`（SERVER/CLIENT 总线定向与跨总线隔离） |
| 4 | commit 前的 packet 不提前进 candidate；commit 后新事件只由新 generation 处理一次，旧 generation 不再接收 | **满足** | `candidatePhasePacketIsServedByActiveAndNeverEntersCandidate`（候选脚本注册监听器后触发包：active +1、候选 0；commit 后新代恰一次、旧代冻结）、`consecutiveReloadsKeepOnlyLatestGenerationReceiving` |
| 5 | watchdog 隔离或 root close 后在途 packet 丢弃或明确失效，不触碰已关闭 Context/timer/binding | **满足** | `isolatedActiveFailureDropsInFlightPackets`（语句上限杀 active → isActiveFailed=true → 包丢弃不抛不触碰、generation 不动、显式 reload 恢复）、`closeDropsInFlightPacketsAndNeverTouchesClosedContext`（close 后包丢弃、closed Context eval 抛/反查抛）、`repeatedCloseAndLatePacketsAreSafe`（幂等 close） |
| 6 | 网络线程异常、非法 channel、超大 payload、坏 NBT 与旧防线一致，不炸平台网络线程、不静默 no-op | **满足（解码拒绝 = 抛异常；「平台转断连」为 loader 行为，见 characterization）** | `NetworkPayloadDefenseLineTest`（65 字符/空 channel 解码 IAE、坏 NBT 解码失败[见 javadoc 的裸 JVM 形态说明]、pack_hashes/pack_bundle 超限 IAE、show_error_list 超长字符串拒绝、空 key IAE）；`listenerExceptionIsContainedAndDoesNotAbortDispatch`（异常不传播进 enqueue 任务、不中断同 channel 后续监听器） |
| 7 | Fabric 缺失的编辑器/显示网络面保持显式子集，不伪造 parity | **满足** | `fabricRegistersExactlyTheExplicitPayloadSubset`（恰 6 处注册调用/5 类型）、`fabricOmitsEditorDashboardAndDisplayPackets`（src/fabric 代码行零 ShowErrorListPacket/ScriptSyncFiles/FetchScriptContent 引用）；FabricPlayNetwork javadoc 修正为显式子集陈述 |
| 8 | 重复 dispatcher、绕过 owner 队列的 receiver、旧直接 Context 路由仅在全 loader fixture 通过后删除 | **满足（无可删项，审计结论）** | 全仓审计：dispatcher 装配恰 2 处（每 loader 一处，无重复）；全部 receiver 先 hop 再投递（fabric 侧 trace 断言，NeoForge 侧 enqueueWork 由 compat seam 测试覆盖）；网络路径无直接 Context 路由（投递只达总线）。保留项：`NekoJSCommands`（共享/1.21.1）对 `ShowErrorListPacket` 的 `PacketDistributor.sendToPlayer` 直发——属显示域发送面直用，不在删除门三类之内，且票文明示不补显示域 parity；保留并在本报告标注（§6-R1） |

### characterization（平台行为，无法 JVM 单测，如实记录）

1. **平台 event/enipoint 单次触发**：`RegisterPayloadHandlersEvent` 每次物理端加载恰发一次、fabric entrypoint 每进程恰调一次，是 loader 契约；JVM 侧以「注册只在 event/entrypoint 内 + reload 面零注册调用 + 平台拒绝重复注册」三面联合钉住。如需运行时计数证据，可用 minecraft-mod-mcp 起客户端做 in-game smoke（本轮未跑，与票 07 G6 同口径）。
2. **解码异常 → 断连**：NeoForge/Fabric 把解码异常转成连接断开（不炸网络线程、不静默）。JVM fixture 钉住的是「decode 必抛」这一输入面。
3. **`Network.sendToServer` 真机路径**：客户端侧 `ClientPacketDistributor`/`ClientPlayNetworking.send` 需要活动连接，无法在裸 JVM 驱动；其 payload 构造语义与发送面同构部分已由 dispatcher 用例覆盖。
4. **坏 NBT 解码的裸 JVM 形态**：`NbtIo.readTagSafe` 失败时构建 CrashReport——游戏进程内为 DecoderException，裸 JUnit 里 CrashReport 静态初始化自身不可用以 Error 冒泡；fixture 断言 Throwable（「绝不解码成 payload」是契约），见测试 javadoc。

## 5. 验证结果（全绿；命令与逐类计数见 `evidence/verification-commands.md`）

| 命令 | 结果 |
|---|---|
| `./gradlew :common:check :common-api-processor:test --console=plain` | **绿** |
| `./gradlew guardLint --console=plain` | **绿**：守卫块 253、扫描 399 文件、豁免 0、警告 0 |
| `./gradlew :26.1.2:build --console=plain` | **绿**（test 实测 220/34 skipped/0 失败） |
| `./gradlew :26.1.2-fabric:build --console=plain` | **绿**（含 verifyFabricRuntimeArtifact；test 114/6 skipped/0 失败） |
| 跨节点专项（26.2.0 / 1.21.1 / 双 fabric 并行） | **绿**：golden hex 五节点全等；routing/defense/trace/shape/fabric-once 全 0 失败 |
| 审查整改复跑（终版文件状态） | **绿**：三节点全套 220/220/139 全 0 失败；双 fabric 全套连续 7 轮并行全 0 失败（trace 终版 9 用例五节点补齐，勘误见 `evidence/verification-commands.md` †） |

五节点全量 build 由主会话合并后统一跑（工单约定）。

## 6. 已知缺口 / 风险与偏离说明

| # | 项 | 说明 | 建议 owner |
|---|---|---|---|
| R1 | `NekoJSCommands` 直发 `ShowErrorListPacket` | 命令面对错误面板包的 `PacketDistributor.sendToPlayer` 直用（共享版 + 1.21.1 版）。显示域属票文排除面（不补 parity），且为 S2C 发送直用而非删除门三类（重复 dispatcher/receiver 绕队列/旧 Context 直路由）；改走 PlayPacketDispatcher 会牵动 fabric `canSend` 语义（fabric 未注册该类型，经 dispatcher 会被静默丢弃——恰好等价于现状，但属行为面变更，本票不为显示域引入） | 显示域票据（如后续做错误面板跨 loader） |
| R2 | in-game smoke 未跑 | AC1 的「注册计数/连接协商不变」运行时证据以 characterization + 三面 JVM 钉住代替；未起真机 | 主会话可用 minecraft-mod-mcp 补一轮（与票 06/07 的 smoke 口径一致） |
| R3 | golden hex 的 NBT 依赖 | script_payload/pdata_sync 的 hex 含 NBT 编码；跨版本一致已在五节点实证，但未来 MC NBT 线格式变化会使 golden 变红——那是受管变更（走迁移表），符合预期 | — |
| R4 | `NetworkGenerationRoutingTest` 的 harness 在 src/test 复刻了 common 测试树的最小装配 | common 测试 fixture（TestPlatformInit/ManagerHarness）不发布给 src/test，无法复用；复刻约 150 行装配 + per-JVM gameDir。若后续 W4 收口测试基建，可上提共享 | W4/测试基建票据 |
| R5 | 并行测试的 tmp 目录冲突（审查中根治） | 实现轮修复 = RoutingTest 自身 gameDir 按 JVM 唯一化，但审查整改验证时**复现**了一次同型 flake——真根因更深：同 JVM 其它测试类（QueryToolDeclarationParityTest `nekojs-query-tools-test`、KeyBindEventsTest `nekojs-kb-test`、EventBusForgeBridge* `nekojs-smoke-test`）用**固定名** tmp gameDir 且 Platform 初始化先到先得，RoutingTest 寄生其上，并行 JVM（双 fabric 节点/跨 worktree）共享固定名目录互清脚本。根治 = `src/test/.../TestGameDirs.unique(base)`（base+PID）统一替换全部固定名 gameDir；6 轮连续双 fabric 并行复跑全绿。教训：`org.gradle.parallel=true` 下任何**固定名** tmp 目录都是雷，即使本类已唯一化也会被同 JVM 先到的固定名初始化寄生 | — |

## 7. 迁移影响

无。wire、注册时机、接收路由、防线行为均保持不变；无公开接口、数据格式或配置变更。
唯一文档性变化是 FabricPlayNetwork 类 javadoc 的子集陈述（不影响字节码语义）。

## 8. 与主工作树未提交删除集的重叠

**无重叠。** 核对事实：主工作树（master）当前无未提交的网络文件删除（`git -C D:/mcmodDemo/NekoJS status` 仅 README 修改与杂项未跟踪文件）；
本分支基线 `eba89230` 的 `git ls-files` 中本就不存在 ScriptSyncFiles/ScriptSyncService/Fetch*/SaveScript/OpenWorkspace/SyncFeedback 等编辑器网络文件
（编辑器移除已在更早提交完成，`NekoJSCommandsEditorRemovalTest` 为其回归）。本票也未新增/复活任何编辑器、dashboard、显示域网络面。

## 9. 审查整改记录（reviewer 判定：修复后合并 → 已全部整改）

| # | 级别 | finding | 整改 |
|---|---|---|---|
| 1 | 必修 | 证据表 26.2.0/1.21.1/26.2.0-fabric 三行的 trace 计数出自旧版 7 用例文件 | `evidence/verification-commands.md` 加 † 勘误 + 「审查整改复跑」终版五节点数字（trace 9/0） |
| 2 | 优化 | hop 用例未真正断言 enqueueWork 发生 | `ImmediateContext` 记录 `enqueueWorkCalls`，server/client 两 handler 各断言 hop 计数 ≥1 |
| 3 | 优化 | 坏 NBT `assertThrows(Throwable)` 过宽 | 捕获后补 `assertFalse(ex instanceof AssertionError)`（异常必源于 NBT 解码路径） |
| 4 | 优化 | FabricNetworkRegistrationOnceTest 泄漏全局 dispatcher 装配 | 两 fabric 节点各加 `@AfterEach` 恢复 `PlayPacketDispatchers.install(NOOP)` |
| 5 | 优化 | trace 扫描自称「全仓 main」却漏 common | `mainJavaFiles()` 纳入 `common/src/main/java`（计数断言不变，common 实测零命中） |
| 6 | 优化 | 重复 close 用例末尾两条自证式断言 | 换成行为断言：close 后再投一次仍安全且零投递；删无用 import |
| 7 | 优化 | FabricPlayNetwork 类首段 javadoc 残留旧表述 | 首段改为「注册 play payload 类型（S2C 同步包 + 双向脚本自定义通道）与对应 receiver」 |
| 附加 | — | 整改验证中复现 RoutingTest 偶发「候选脚本零执行」 | 根因 = 固定名 gameDir + Platform 先到先得寄生（§6-R5 修订）；新增 `TestGameDirs.unique`（base+PID）替换全部固定名 gameDir，6 轮双 fabric 并行复跑全绿 |
