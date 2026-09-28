# Ticket 21 平台接线 review pack（2026-09-29）

维护者裁决输入（"现在备方案+diff"）：Dynamic Registry 批事务（票 21）如何骑上票 17
的 register-once payload 通道族、26.1.2 共享树的真实现状、冻结 wire gate 的旧/新
diff 与安全论证。**本 pack 全部内容停留在 `ticket-21-wire-prep` 分支，不合并不推送；
gate 更新的生效以维护者对本 pack 的裁决为准。**

- 分支：`ticket-21-wire-prep`（基于 mult@`973defbc`）
- 执行者：zed-flash-21 subagent（GLM-5.3）
- 交付物：本目录（REPORT / `wire-gate-oldnew.patch` / `command-output/*.txt`）

---

## 1. 设计（备方案）

### 1.1 通道与 payload

**一个新 payload 类型，零新通道，零新注册点。** `nekojs:dynamic_registry_sync`
（双向，play 阶段）注册在既有 `RegisterPayloadHandlersEvent` 入口（`NekoJSNetwork`，
channel `"1"`），经 `McPlatformCompat.Impl#registerDynamicSyncPayload` 版本门面——
与 `registerScriptPayload` 同款下沉模式，`NekoJSNetwork` 保持零版本守卫。26.x
（Nf261/Nf262）实现真注册；1.21.1 无动态注册面，默认空实现＝显式能力声明（非故障
屏蔽）；fabric 显式子集不变（仍恰 6 调用/5 类型）。

payload 主体是**单条 UTF-8 JSON 字串**（`ByteBufCodecs.STRING_UTF8`），schema
`v:1`，编解码在 common 的 `DynamicSyncWireCodec`（协议与线格式同源，无第二模型）：

```
s2c  {"v":1,"kind":"PREPARE|STATE_SYNC|COMMIT|ABORT","generation":12,"reason":"…",
       "entries":[{"definition":{"type":"ITEM","id":"mymod:ruby","mode":"WORLD",
                    "readings":["maxStackSize=16","mode=world","rarity=epic",…],
                    "fingerprint":"<sha256>"},"owner":"server_scripts/main.js"}]}
c2s  {"v":1,"kind":"ACK","generation":12,"accepted":true,"reason":null}
     {"v":1,"kind":"ACTIVATION_REPORT","generation":12,"activated":false,"detail":"…"}
```

全量状态（非 delta）语义沿用 coordinator 既有设计（`DynamicSyncMessage` 类注释：
fingerprint 一经暴露不可变 ⇒ 全量幂等且自愈）。解码严格：未知 schema 版本/类型/
mode/kind 一律 `IllegalArgumentException` 拒绝，接收面丢弃并 WARN（票 17 防线口径，
不炸网络线程）。

### 1.2 组件与线程跳转

| 组件 | 位置 | 职责 |
|---|---|---|
| `DynamicSyncReply` + `DynamicSyncWireCodec` | common `core.dynamic.txn` | C2S 回复模型 + JSON codec（零 MC 类型，`:common:checkCommonIsolation` 过） |
| `DynamicRegistrySyncPacket` | 共享树 `network`（neoforge+>=26 守卫） | payload record：`{json}` 一字段 + id/codec |
| `NeoForgeDynamicRegistryAdapter` | 共享树 `dynamic` | 真实 surgery：读数→注册值重建，只经既有 `DynamicRegistries` 冻结旁路路径 |
| `NeoForgeDynamicSyncTransport` | 共享树 `dynamic` | 参与者判定（远程玩家）+ 逐发/广播投递（不抛契约） |
| `DynamicRegistrySyncWire` | 共享树 `dynamic` | 服务端装配：`@EventBusSubscriber` 生命周期/tick/加入/离开 + 回复接收 |
| `DynamicRegistryClientSync` | 共享树 `dynamic` | 客户端 participant 持有 + S2C 分发 + 断线清理（仅客户端 dist 装配） |

**线程纪律（票 17 接收面同款）**：网络线程收包 → `IPayloadContext.enqueueWork` 切
平台主线程 → 才进 coordinator/participant（两者 owner-thread-only）。发送面在 owner
线程直接经 `PacketDistributor`/`ClientPacketDistributor`。`DynamicSyncWiringSourceTraceTest`
用源码 trace 钉住两端 handler 的 enqueueWork hop 与「注册只在 compat 实现」。

### 1.3 owner 调度与生命周期

- **绑定**：`ServerAboutToStart` 时（gate 开启）`bindActivationEngine(adapter,
  transport, 10s ackTimeout, wall clock)`；与 `fireInitialCollection` 的相对顺序
  无关——facade 的 `pendingCandidates` 是 pull 队列，晚绑定的引擎由下一个
  `ServerTickEvent.Post` 的 `pumpActivation()` 补上同一批次（`lastStagedPlanGeneration`
  水位防重复）。**共享监听器文件零改动**（ServerEventListener/PlayerEventListener
  未触碰；接线自包含在 >=26 守卫的 `DynamicRegistrySyncWire`）。
- **驱动**：每 server tick post → `pumpActivation()`（reload 后的批次排队进引擎）+
  `engine.tick(now)`（ack 超时）。
- **参与者**：join → `onParticipantJoined`（事务中=补 PREPARE 并要求 ack；否则
  STATE_SYNC 追平）；leave → `onParticipantLeft`（未 ack ⇒ 整批 abort）。
- **收尾**：`ServerStopped` → `clearActivationEngine("server-stopped")`（在飞 abort +
  队列丢弃，coordinator 既有 close 语义）。客户端断线 → `onDisconnect()`（丢 staged，
  账本/水位保留）。
- **重连/追平**：join 时 STATE_SYNC 全量追平（coordinator 已设计）；post-commit 客户端
  激活失败 → `onParticipantActivationReport(false)` → 记录 + 追平 STATE_SYNC 修复
  （票 21 主会话复核已有语义，平台接线把它接到真回包）。

### 1.4 gate（engine.toml `[dynamicRegistry]`）

沿用旧直注路径的既有开关：**默认 false ⇒ 引擎不绑定**，本面行为与票 16 inert 面
完全一致（声明+账本，激活继续被阻塞）；客户端 gate 关闭时 PREPARE/STATE_SYNC 直接
ack-rejected（整批在服务端 abort）——两端显式选择加入。这是本 pack 不放行"公开激活"
的保守口径：即便维护者批准接线，真实热更新仍需 pack 作者显式开 gate + 真机验证后
才在能力表从 not verified 改口。

### 1.5 Adapter 真实现状（诚实口径）

- **Item / SoundEvent / MobEffect 三类都有真 surgery 路径**：`DynamicRegistries` 的
  冻结旁路注册（`RegistrySurgery`，Katton 移植的既有机制——26.1.2.71/26.2.0.57 双
  映射验证过的反射手术）。**没有类型被标 explicit-unavailable**。
- **回滚**：`rollbackActivation` 只注销本次 activate 尝试**新注册**的条目（重 claim
  的既有条目保留——失败前已合法存在），经 `DynamicRegistrySet.unregisterTrusted`
  （`RegistrySurgery.unregisterAll` 全索引手术 + claim/ITEM_SPECS 清理）。这是把
  既有 world-leave 清理手术复用为回滚通道，非新机制。
- **客户端限制（记录，非隐藏）**：客户端重建 fire-resistant item 的 tag 支撑用进程级
  `RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY)` 投影——若接收
  时点 DAMAGE_TYPE tag 未绑定，激活按契约失败并回报，服务端 STATE_SYNC 追平修复
  （有界重试：失败 ack 记录后不再自动重发）。服务端不受此限（`server.registryAccess()`，
  tag 已绑定）。

---

## 2. 安全论证（wire 兼容）

**为什么增量安全**（对应 `wire-gate-oldnew.patch`）：

1. **同一 channel、同一注册点**：新 payload 注册进既有 `registrar("1")` 的
   `RegisterPayloadHandlersEvent`——无第二个 install/注册点
   （`dispatcherInstallAppearsExactlyOncePerLoaderInMainSources` 仍恰 2 不变；
   `PlayPacketDispatchers` 零改动）。
2. **纯增量类型**：6 个既有 payload 的 id/codec/线格式零触碰
   （`PayloadWireFormatGoldenTest` 零改动；新增的
   `DynamicSyncPayloadWireFormatTest` 只钉新 payload 自己的 id/hex）。
3. **旧客户端对未知 payload 类型的行为**（票 17 频道纪律验证）：NeoForge 26.x
   payload 协商按 channel 版本串（`"1"` 未变）+ 类型 id 协商——旧端（无
   `nekojs:dynamic_registry_sync` 注册）不会收到未知类型：新版服务端只在
   participant 集合内投递该类型，NeoForge 对未注册类型的入站包在连接协商层已隔离。
   payload 集合增量 + channel 版本不变 ⇒ 已有连接与新连接的既有 6 类型行为不变。
4. **fabric / 1.21.1 零影响**：fabric 显式子集断言（恰 6 调用/5 类型）**原样通过、
   未改动**；1.21.1 经 compat 默认空实现显式不注册（类本身 `>=26` 守卫缺席）。
5. **gate 更新本身是受管变更**：`NetworkRegistrationSourceTraceTest` 只增加「新 payload
   必须经 compat 门面注册在同一 loader 事件」的方向钉住 + javadoc 记录增项与裁决
   条件——没有任何既有断言被放松或删除（diff 全为 +）。

---

## 3. 逐文件 diff 摘要

**common（`core.dynamic.txn` / `plan`）**

| 文件 | 变更 |
|---|---|
| `txn/DynamicSyncReply.java` | 新增：C2S 回复模型（ACK / ACTIVATION_REPORT），string/enum/number 纪律 |
| `txn/DynamicSyncWireCodec.java` | 新增：双向 JSON codec（schema v1，严格解码） |
| `plan/DynamicAdapterRequest.java` | **契约增项**：新增 `readings` 组件（规范化读数随请求下发，Adapter 据此重建注册值，无需回查 builder）。纯数据；inertness 性质不变（无执行通道/MC 类型） |
| test `plan/DynamicPlanInertnessTest.java` | 位置构造调用 +1 实参（`readings`）；**断言零放松**（构造器恰 2、无执行通道、三类型封闭全部原样） |
| test `txn/DynamicSyncWireCodecTest.java` | 新增 6 用例：双向 round-trip、严格解码（schema/kind/type/mode）、确定性编码 |

**共享版本树（`src/main`，neoforge+>=26）**

| 文件 | 变更 |
|---|---|
| `network/DynamicRegistrySyncPacket.java` | 新增：payload record（id `nekojs:dynamic_registry_sync`，UTF8-JSON 单字段） |
| `dynamic/NeoForgeDynamicRegistryAdapter.java` | 新增：prepare 校验（gate/类型/mode/id/读数）→ activate 真手术 → rollback 只撤新注册条目 |
| `dynamic/NeoForgeDynamicSyncTransport.java` | 新增：participants=远程玩家（`isSingleplayerOwner` 排除共享 JVM 的主机玩家）；逐发/广播不抛 |
| `dynamic/DynamicRegistrySyncWire.java` | 新增：服务端 `@EventBusSubscriber`（绑定/tick/join/leave/收包 hop 分发） |
| `dynamic/DynamicRegistryClientSync.java` | 新增：客户端 participant + S2C 分发 + 回包 + 断线清理（仅客户端装配） |
| `dynamic/DynamicRegistries.java` / `DynamicRegistrySet.java` | 增：包内 `rollbackEntries` / `unregisterTrusted`（复用既有 unregister 手术） |
| `network/NekoJSNetwork.java` | +1 行：`registerDynamicSyncPayload(registrar)` 经 compat 门面（零版本守卫保持） |
| `platform/compat/McPlatformCompat.java` | +default 方法：`registerDynamicSyncPayload`（1.21.1 空实现=无该面） |
| `NekoJSMod.java` | +guard 行：客户端 dist `DynamicRegistryClientSync.install()`（>=26） |
| `versions/26.1.2`/`26.2.0` `Nf26xPlatformCompat.java` | 各 +1 override：4 参 `playBidirectional` 注册（与 script_payload 同形状） |

**测试（共享测试树）**

| 文件 | 变更 |
|---|---|
| `network/NetworkRegistrationSourceTraceTest.java` | **冻结 gate 受管更新**（见 §2 与 patch）：新 payload 方向钉住 + javadoc |
| `network/DynamicSyncPayloadWireFormatTest.java` | 新增：wire golden（id + 3 组 hex 实测 + 解码闭环，>=26 守卫） |
| `dynamic/DynamicSyncWiringSourceTraceTest.java` | 新增：接线纪律 trace（注册恰在两 compat 实现 / 两端 hop / 无第二编码） |

未触碰：contract/golden、TS/Python declaration、`PayloadWireFormatGoldenTest`、
fabric 全部文件、`versions/1.21.1`、共享监听器文件（ServerEventListener/
PlayerEventListener/PDataSyncListener）。

---

## 4. 验证

| 命令 | 结果 |
|---|---|
| `:common:check`（含 checkCommonIsolation） | **通过**（`command-output/common-check.txt`；1945 tests / 0 failed） |
| `:26.1.2:test`（clean `--rerun` 全量） | **通过**（`command-output/26-1-2-test.txt`） |
| `:26.2.0:compileJava` | **通过**（共享树第二 NeoForge 节点） |
| guardLint | 未跑（未触碰 build 文件/源根/守卫表；新文件位于既有源根且沿用既有守卫形态） |
| 1.21.1 / fabric 节点 | 未跑（新代码全部 `neoforge`+`>=26` 守卫缺席于这些节点；fabric 显式子集断言在 26.1.2 全量里已同跑同过——trace test 是全节点同跑设计，但本 pack 只在主节点取证） |

golden hex 来源：26.1.2 active 节点真实 codec 编码输出（capture 运行：commit/prepare/
ack 三体；临时 printer 测试已删除，常量进 `DynamicSyncPayloadWireFormatTest`）。

## 5. 真机多人未验证面（owner 34）

- 双进程真机 smoke（dedicated server + 远端客户端的 PREPARE→ack→COMMIT 全链路、断线
  重连 STATE_SYNC 追平、LAN 主机排除）——**未跑**，JVM 级 fixture 覆盖的是协议语义与
  源码纪律，不是平台连接协商/序列化路径的真机形态。
- 客户端 fire-resistant item 的 tag 绑定时点（§1.5）——JVM 无法复现客户端 join 时序。
- 能力表口径不变：三类候选类型的目标 Adapter **真机同步仍 not verified**，公开激活
  仍阻塞（gate 默认关 + AC9/AC10 口径不动）。

## 6. 维护者被请求裁决的事项

1. **wire gate 增项**：批准 `NetworkRegistrationSourceTraceTest` 的受管更新（+方向
   钉住 + javadoc）与第 7 个 payload 类型 `nekojs:dynamic_registry_sync`（同 channel
   同注册点，fabric 子集不动）——即批准本 pack 的 `wire-gate-oldnew.patch` 合并。
2. **common 契约增项**：`DynamicAdapterRequest.readings` 组件（数据面，inertness
   断言零放松）。
3. **gate 策略**：`[dynamicRegistry]` 默认 false 的保守口径是否维持到真机 smoke
   （owner 34）之后再议公开激活。
