# 票 24 证据报告：Block/Item/Level/Player/Command/Capability/Goal/Entity 既有事件面覆盖路径

- 执行者：zed-flash-24（GLM-5.3 subagent worktree `../NekoJS-mult-t24`，分支
  `ticket-24-gameplay-event-surface`，基于 mult@5cb86e2e）。
- 输入（characterization，本票不改其语义）：八个事件族声明（共享树 + fabric 孪生 +
  Fabric*Bindings v1/v2 Adapter）、`NekoScriptCatalog.events` 派生、`EventBusJS` /
  `EventGroup` / `DefaultScriptEventBridge` 的候选-提交换装链、票 39 的
  `ModificationDomainOwner`（modification 家族 posting site，本票只验证事件面接线）、
  票 33 的 `event-surface-domains.txt` 只读基线。
- **本票对生产主源码（src/main、common/src/main、src/fabric、versions/*/src/main）零改动**；
  全部交付物为测试树 fixture、两处既有测试的生产次序初始化修复、baseline 证据与示例。

## 1. 范围与实现摘要（what changed）

### 1.1 新增测试（版本树共享测试树 + fabric 节点本地树）

| 文件 | 内容 |
|---|---|
| `src/test/.../bindings/event/Ticket24GameplayEventCatalogTest.java`（neoforge 守卫） | AC1 契约快照：生产注册入口（`NekoJSCorePlugin.registerEvents/registerClientEvents`，含 `NeoForgeBlockEvents.bootstrap()` 与 cancellability predicate 的生产次序）→ `NekoScriptCatalog.events` 派生 → 与冻结表逐成员比对（名/payload/side/dispatch 键/cancel）。新增/删除/改形任何公开成员 = 表 diff；家族缺席直接红。2 用例（含每 bus 恰一条目录条目）。 |
| `versions/26.1.2-fabric(26.2.0-fabric)/src/test/.../Ticket24FabricGameplayEventCatalogTest.java` | fabric 同款快照：生产次序（v1/v2 Adapter 类初始化追加成员 + `FabricCorePlugin.registerEvents` 同名合并）→ 同一 catalog 派生。**覆盖票 33 基线看不见的 fabric 运行面**（见 §5-D1）。1 用例/节点。 |
| `src/test/.../Ticket24GameplayEventReloadHarness.java` + `Ticket24GameplayEventReloadLifecycleTest.java` | AC4 主证（真实 Graal 管线 + **生产 `DefaultScriptEventBridge`**，非接口 legacy fallback）：多次 reload 每代恰一次派发、失败候选保留旧 active 且下一轮恢复、取消结果跟随 active generation、实体六行为（join/leave/damage/death/drops/finalizeSpawn）同代换装、goal 家族 posting site 往返。6 用例。 |
| `src/test/.../Ticket24GameplayFamilyBusBehaviorTest.java` | AC2/AC3 bus 级行为：真实家族总线上的注册、payload 原样透传、priority 次序（HIGHEST→LOW）、取消短路与 post 返回值、多次订阅恰一次、dispatch key 定向（mainBus 先于 keyed）、posted-object 面（modification 不可取消不分发）、goal/capability posting site、JS 记账与 bridge 记账的清理所有权分界。8 用例。 |
| `src/test/.../Ticket24GameplayEventPhaseTraceTest.java`（全节点，纯文件读） | AC3/AC5/AC9/AC10 源码 trace：每族 FORGE_BRIDGE 绑定数逐一冻结（ItemEvents 8/LevelEvents 10/PlayerEvents 17/CommandEvents 2/EntityEvents 13/Block 12+1 transformed）；posted-object/listener-posted 总线**不在** bridge 上且各有唯一 production poster（capability/goal/inventoryChanged/modification×2/broken-fabric）；双逻辑侧事件的 `!isClientSide` 过滤逐文件计数（含 1.21.1 孪生）；fabric Adapter/mixin 存在性与 entity 行为 post 面；`EventGroup.of("<族名>")` 只出现在合法声明点（AC10 无第二注册路径）。6 用例。 |

### 1.2 既有测试的两处修复（共享测试 JVM 的生产次序对齐）

| 文件 | 变更 | 理由 |
|---|---|---|
| `EventApiSurfaceGoldenTest` | 测试体内先装 `ICancellableEvent` predicate 再 bootstrap | 生产次序是 `NekoJSMod` 构造器 → `NeoForgeRuntimeBootstrap.setup()` 先于任何族类初始化；此前测试 JVM 会把全部可取消家族总线冻成不可取消，任何 cancel 语义断言都无从谈起 |
| `EventSurfaceOwnershipTest` | registeredGroups() 先装 predicate + Platform stub | 同上；另修复：`registerClientEvents` 触达 `KeyBindEvents` → `NekoJSMod` 静态块安装 `NeoForgePlatform`（gameDir 需真实 FML），毒化同 JVM 后续所有需要 scriptsDir 的 harness（本票 lifecycle 测试在组合运行中真实踩中，NPE 栈见 git 历史/过程记录） |

### 1.3 明确不动

- 生产主源码零改动；所有 mutation 实验已还原（`git diff` 仅上述两测试文件 + 新增文件）。
- 未新增事件 bus、未新增 Extension Point、未动第二注册路径（trace 冻结现值为证）。
- 未更新任何 golden/declaration/manifest/`event-surface-domains.txt`（只读约束）。
- 未触碰票 30 诊断域文件、tickets README、AGENTS/CONTEXT、`docs/adr/`、sibling worktree。
- Item/Block modification 只验证事件面接线（poster 唯一性 trace + catalog 形状）；事务/snapshot
  验收归票 39 证据（`baseline/2026-09-16-item-block-modification/`）。

## 2. 逐条 AC 状态（判定明细见票 24 文档 AC 区）

| AC | 状态 | 依据 |
|---|---|---|
| 1 无遗漏家族 / drift=diff | 满足 | 两 loader 的 catalog 快照测试（真实派生 vs 冻结表）+ 票 33 基线三节点复验 0 失败 0 member-drift（command-output/05–07）；**fabric 运行面比基线大**（D1，owner 33/34，不改只读基线） |
| 2 每族 caller-to-result；CommandEvents 不混票 20；modification 只接线 | 满足 | block(bus+trace)、item(bus+trace+modification 接线)、level(e2e+trace)、player(bus+e2)、command(cancel e2e+trace)、capability(bus+posting site)、goal(posting site e2e)、entity(六行为 e2e+bus+trace)；`CommandEvents.register/command` 与票 20 命令树无交集（trace：/nekojs 命令注册不在本票面） |
| 3 representative path 可追踪 | 满足 | 脚本注册→总线→payload→执行/取消结果在 e2e/bus 测试真跑；平台原生回调腿为 source trace + 既有 `EventBusForgeBridgeTest`/`SideFilterTest`（无头 JVM 边界，AC9 记 smoke 缺口） |
| 4 priority/cancel/返回值/多次订阅 + 并发无重复/半清理 | 满足 | priority/cancel/multi-subscribe/reload 全绿；突变 C（双重激活）红→红原文 4 用例、突变 D（取消被忽略）红→还原绿（command-output/03）；清理的三层防御 characterization 见 §4；并发 stress 本体 = common `EventBusJSExternalBehaviorStressTest`（`:common:check` 内全绿） |
| 5 side/能力显式、不静默 no-op | **部分满足** | side（CLIENT tooltip/STARTUP goal+capability/其余 SERVER）与双逻辑侧过滤冻结在快照+trace；fabric 能力差异逐项显式（command/useItem* 缺席、chat/dropped/finalizeSpawn 不可取消、CapabilityEvents 缺席）；**D2**：`broken` 取消在全部加载器上静默 no-op（文档却记可取消）——已显式记录但运行时仍 no-op，修复=行为变更需维护者裁定 |
| 6 实体六行为各 ≥1 fixture | 满足 | join/leave/damage/death/drops/finalizeSpawn 在 `entityFamilyLifecycleListenersFollowTheSameGenerationSwap` 同一 e2e（两代换装各断言一次/零次）+ fabric 侧 catalog/trace |
| 7 capability/goal 不与启动 registry/交易/客户端混 owner | 满足 | 两族 STARTUP 面、posting site 唯一（trace）、无 registry mutation 参与；fabric core plugin 零 CapabilityEvents 触点（显式缺席断言） |
| 8 TS/Python declaration 一致；普通测试不更新 golden | **部分满足** | declaration 面对本票八域 **0 命中**（command-output/04，与票 26/28/23 同类缺口）→ owner 09/33/34；本票零 golden 改动 ✓；已交付 catalog 维度四列快照作为 runtime 侧事实源 |
| 9 source trace + runtime smoke 按支持等级 | **部分满足** | source trace 六用例（真实文件读，非反射清单）+ 三节点 platformGateTest 复验；**真机 runtime smoke（runGameTestServer/真实游戏）未跑** → owner 34 |
| 10 不新增平行事件/Point/第二注册路径 | 满足 | 生产零改动；`EventGroup.of("<族名>")` 声明点计数冻结（Block/Capability/Goal=1、Level/Command=2、Item/Player/Entity=3），bridge 绑定数冻结 |
| 11 收缩 gate | 满足（零删除） | 本轮盘点未发现需移除的旧 wrapper/binding/声明旁路：唯一 poster 已逐项钉住，无第二注册路径，deprecated 别名（pickedUpPre/tick/before/afterExplosion/inventoryOpened/Closed）为公开功能**不删**；`GoalRegistry` 进程级静态（GOALS/APPLIED_JOIN_GOALS）为 STARTUP 生命周期状态（绑定语义同 KeyBindEvents 既有裁定），不是 reload 旁路——characterization 记录，不动 |

## 3. 红与绿（red → green）

1. **突变 C（重复 dispatch 缺陷形态，AC4 主证）**：`EventBusJS.activatePending` 注入第二注册 →
   4 用例红（`expected: <[gen1]> but was: <[gen1, gen1]>` 等）；还原 → 6/6 绿。
2. **突变 D（取消被忽略）**：`registerCancellable` 忽略脚本 true →
   `cancellationResultIsObservableFromThePostSide` 红（`expected: <true> but was: <false>`）；
   还原 → 绿。
3. **平台毒化真红（非注入）**：`EventSurfaceOwnershipTest`（无 Platform stub）先跑时，
   `KeyBindEvents`→`NekoJSMod` 静态块把 `NeoForgePlatform` 装进测试 JVM，lifecycle harness 在
   `scriptsDir` 上 NPE（`FMLPaths.get() is null`）→ 给该测试补生产次序 stub 后组合运行绿。
   这是共享测试 JVM 的初始化次序缺陷，被本票 fixture 捕获并修复。
4. **1.21.1 真红**：catalog 测试首跑 `BlockModificationEventJS` 找不到（import 未随 `>=26`
   守卫剥离）→ 守卫 import 后绿。
5. **fabric 首跑真红**：快照表先按票 33 基线写成 broken/modification 两成员 → 真实运行面多
   10 个 BlockEvents 成员（v1/v2 Adapter 类初始化追加）与 5 个 LevelEvents 成员 → 按真实
   运行面扩表后绿（发现记录为 D1）；`ItemEvents.entityInteracted` payload 同名类混淆
   （wrapper.event.item vs .player）一并修正。

全部原文见 `command-output/03-red-green-mutations.txt`。

## 4. 「无重复 dispatch」的防御纵深（characterization）

逐层摘除实验（command-output/03 尾节）：只摘 commit 点旧 token 的 `bus.unregister`（层 1）、
再摘 dead-context 跳过守卫（层 2），观察面仍是每代恰一次——因为旧 generation 的 Graal
Context 已 close，残余监听器执行即抛、被分发包装 catch 记为回调错误，不产生可见派发（层 3，
无法用单点突变摘除）。只有真正的双重激活（突变 C）才让重复派发可观察。结论：该性质由
三层独立机制冗余保证；fixture 对可观察缺陷形态敏感（C/D 均红）。

## 5. 发现与缺口（owner 明确）

- **D1（基线低记，owner 33/34）**：票 33 `event-surface-domains.txt` 的 gate 只驱动
  `registerEvents` 钩子；fabric 侧由 `NekoJSFabricMod.onInitialize` 早期 Adapter 类初始化
  （`FabricBlockEventBindings`/`V2`、`FabricLevelEventBindingsV2`）追加的 BlockEvents×10、
  LevelEvents×5 成员不在其输入内。本票 fabric 快照按真实运行面冻结并留档
  （catalog-snapshot.md）；基线文件本票不改（只读）。
- **D2（行为缺陷，需维护者裁定）**：`BlockEvents.broken` 被文档记为可取消
  （wiki 事件参考「可取消」；`BlockBrokenEventJS` @Doc 甚至写「Return false to cancel」，
  与全仓统一的 return-true 取消约定相悖），但中立 payload 不实现 `ICancellableEvent`，
  predicate 把总线冻成不可取消——**四个节点全部静默 no-op**；fabric 桥的
  `!BROKEN.post(...)` 反转接线因此恒为「放行」。修复（令总线显式可取消）会激活两加载器
  的取消路径，属公开行为变更，超出本票盘点授权。
- **D3（declaration 缺口，owner 09/33/34）**：八个事件域在 TS/Python declaration golden 与
  parity 基线中 0 命中（command-output/04）。
- **G1（真机 smoke，owner 34）**：runGameTestServer / 真实游戏事件未跑（本票全部证据为
  无头 JVM + 源码 trace + 跨节点 gate 复验）。
- **G2（NEKO- 码注册表）**：本分支无 `wiki/en_us/Error-Reference.md`，新错误文本只能用
  普通 IAE/ISE 英文消息、不编码号——本票未新增生产错误文本，无实际影响；码表缺失已在此
  记录。
- **G3（capability 家族 fixture 深度）**：`CapabilityRegistryEventJS.apply` 需要 NeoForge
  mod-bus `RegisterCapabilitiesEvent` 真机对象，无头 JVM 只覆盖到 posting site 与载荷构造
  （bus 测试），注册应用归真机 smoke（G1）。

## 6. 验证命令与结果（真实执行）

| 命令 | 结果 |
|---|---|
| `gradlew :26.1.2:test --tests "*Ticket24*" --tests "*EventApiSurfaceGoldenTest*" --tests "*EventSurfaceOwnershipTest*" --rerun` | BUILD SUCCESSFUL（28 用例：Catalog 2 + PhaseTrace 6 + ReloadLifecycle 6 + FamilyBus 8 + EventApi 1 + EventSurface 3，全绿） |
| `gradlew :26.1.2:test`（全套） | BUILD SUCCESSFUL；tests=407 failures=0 errors=0 skipped=58 |
| `gradlew :1.21.1:test --tests "*Ticket24*" --tests "*EventSurfaceOwnershipTest*" --tests "*EventApiSurfaceGoldenTest*"` | BUILD SUCCESSFUL |
| `gradlew :26.1.2-fabric:test --tests "*Ticket24*"` | BUILD SUCCESSFUL（含 Ticket24FabricGameplayEventCatalogTest 1 用例 + 共享树 trace/lifecycle-guarded 求值） |
| `gradlew :common:check` | BUILD SUCCESSFUL in 1m34s（含隔离检查；common 主源码零改动，跑作回归底线） |
| `gradlew :26.1.2:platformGateTest --rerun` | BUILD SUCCESSFUL；event-surface 17 domains / 0 failures，无 member-drift（JSON 快照 command-output/05） |
| `gradlew :1.21.1:platformGateTest --rerun` | BUILD SUCCESSFUL；15 domains / 0 failures（command-output/07） |
| `gradlew :26.1.2-fabric:platformGateTest --rerun` | BUILD SUCCESSFUL；14 domains / 0 failures（command-output/06；D1 的输入边界不影响 gate 判定） |
| 突变 C/D 与 A+B | 见 §3 与 command-output/03（红→绿原文） |
| `gradlew :26.1.2:test`（harness 换生产 bridge + 实体六行为扩表后最终复跑 --rerun-tasks） | BUILD SUCCESSFUL in 1m20s；tests=407 failures=0 skipped=58（lifecycle 单独复跑 6/0 亦绿） |
| `guardLint` | **未跑**（无 build/source-root/guard 改动） |
| `runGameTestServer` / 真机 smoke | **未跑**（G1，owner 34） |

golden/基线：本票零改动（`event-surface-domains.txt`、`block-events-api.txt`、declaration
goldens、api-manifest 均原样）；`platformGateTest` 三节点复验无 drift。

## 7. 示例与迁移材料

- `examples/gameplay-events.js`：六族 server 脚本最小示例（成员名按 26.x 原生载荷 javap
  核对；含 D2 警示注释与 deprecated 别名迁移提示）。
- `examples/goal-capability-events.startup.js`：STARTUP 两族示例（fabric 缺席显式注明）。
- 无公开面删除/改名 → 无迁移表条目；deprecated 别名的迁移提示已写入示例注释（与源码
  `@Deprecated` 注释一致）。

## 主会话复核修正（2026-09-22，合并后）

- 四个新测试文件（Catalog/ReloadHarness/ReloadLifecycle/FamilyBusBehavior）的 23 处
  中文注释块翻译为英文（AGENTS.md 语言规则；PhaseTrace 本就全英文）。仅注释行变动，
  代码/字符串字面量零改动（diff 逐行核对）。
- 复核确认：两处既有测试修改（EventApiSurfaceGoldenTest/EventSurfaceOwnershipTest 装生产
  cancellability predicate + 平台 stub）为真实测试环境修复、未删任何断言；catalog 快照经
  真实注册入口派生、八族齐全；D1（票 33 fabric 门禁低估）/D2（broken 取消静默 no-op，
  待维护者裁决）/D3（declaration 零覆盖）维持记录不动。

## 8. 证据收口（2026-09-28，AC9）

执行者：zed-flash-24（GLM-5.3 subagent worktree `../NekoJS-mult-t24s`，分支
`ticket-24-smoke-evidence`，基于 mult@64f5ea95）——本票实现合并后的 AC9 证据补齐轮，
生产主源码零改动；AC5/AC8 与 goldens 未触碰。

### 8.1 真机 runtime smoke（26.1.2 NeoForge dedicated server，command-output/08）

- `./gradlew.bat :26.1.2:runServer --console=plain`（BUILD SUCCESSFUL in 59s，clean stop）：
  真实 FML/NeoForge 26.1.2.71 游戏进程；冒烟脚本放
  `versions/26.1.2/run/nekojs/{startup_scripts,server_scripts}`，本机 RCON（127.0.0.1:25581）
  驱动探针后 `stop` 干净关服（ServerEvents 停机链完整，LevelEvents.unloaded×3）。transcript
  内嵌两份冒烟脚本与 RCON driver 源码，可复现。
- 命中的家族/成员（marker 全录于 transcript，逐行带原始 log 行号）：
  - STARTUP：脚本加载、GoalEvents.register / CapabilityEvents.register posting site 各一次；
  - modification（posted-object，票 39 域接线）：Item/Block 两个合法 posting 点均真跑
    （reload DOMAIN_PLAN 线程 + ServerAboutToStart applyInitialPlan）；
  - LevelEvents：loaded×3 维度、tickPre 首 tick、explosionStart（summon tnt 真实爆炸）、
    unloaded×3（stop）；
  - CommandEvents.command：HIGHEST 先于 NORMAL（逐命令可见，行号 21413→21414 连续）；
    listener return true 取消 say——被取消命令零广播、对照命令正常广播；
  - EntityEvents（summon spider 为载体）：finalizeSpawn、joinLevel（spider+tnt）、damagePre
    载荷 originalDamage=5/7/1000、damagePost、death、drops、leaveLevel 全链；
  - BlockEvents：blockEntityTick（setblock hopper 真实 tick）；
  - PlayerEvents：无客户端不可驱动（loggedIn 监听已挂、marker 预期缺席）→ 客户端侧归票 34。
- 两次准备运行（脚本成员名修正、forceload 策略——现代 dedicated server 无玩家不保持区块
  加载）过程留档于 transcript 头注，不属证据本体。

### 8.2 节点套件（command-output/09）

- `:26.1.2-fabric:test` 251 tests / 0 fail 0 error / 25 skipped（此前本票只跑过
  `--tests "*Ticket24*"` 过滤）；
- `:26.2.0-fabric:test` 251 tests / 0 fail 0 error / 25 skipped（本票从未跑过）；
- `:26.2.0:test` 448 tests / 0 fail 0 error / 58 skipped（本票从未跑过全套件）。
- 命令：`./gradlew.bat :26.1.2-fabric:test :26.2.0-fabric:test :26.2.0:test --continue
  --console=plain` → BUILD SUCCESSFUL in 28s（编译产物刚被 runServer 轮预热；三个 :test
  任务均真实执行，XML 时间戳与计数见 transcript）。fabric 能力立场（capability/goal 缺席、
  useItem*/tick* 不可用、CommandEvents.command 缺席）随含 Ticket24FabricGameplayEventCatalogTest
  的全套件复验。

### 8.3 冒烟新发现（记录不修——修复=生产行为变更，待维护者裁定）

- **D4（damagePre 取消静默 no-op + snapshot 表 cancel 列手抄错误）**：
  `LivingDamageEvent.Pre` 在 NeoForge 21.1.227 与 26.1.2.71 均不实现 ICancellableEvent
  （sources 已核），cancellability predicate 把总线冻成不可取消；真机实测：脚本取消分支
  打印 `T24-ENTITY-DAMAGE-CANCEL` 并 return true 后，5 伤害仍生效（生命 16.0→11.0）、
  damagePost 仍触发。脚本侧正确改伤入口是 `setNewDamage(0)`。
  `catalog-snapshot.md` 的 damagePre cancel 列原记 `true`，与冻结测试的动态求值（false，
  测试一直绿）和真机均不符——属手抄错误，本轮已就地更正并加注。与 D2（broken）同类：
  文档面与运行面不一致，修复需维护者裁定。
- **D5（randomTick 对原版随机 tick 方块不可达）**：两 loader 孪生 mixin 均注入
  `BlockBehaviour` 接口 default `randomTick` HEAD，而 `BlockStateBase.randomTick` 虚分派到
  具体方块覆写（草方块=SpreadingSnowyBlock.randomTick）；接口 default 体为空，且原版无任何
  「随机 tick 却不覆写 randomTick」的方块（minecraft-patched-26.1.2.71 源扫描 0 命中）→
  事件在原版随机 tick 方块上永不可达。真机：forceload 区显式放置草方块 + ~25s 窗口无
  marker，同区块 blockEntityTick/level tick/entity 事件全部命中。fabric 孪生 mixin 注释
  已载明「子类覆写不经过基类」（作为已知语义），但该成员在目录/wiki 面按可用家族展示，
  换注入点属生产行为变更，待维护者裁定。另记：26.x 的 `randomTickSpeed` gamerule 名已变
  （探针报 `Incorrect argument`），票 34 真机脚本注意。
- AC5/AC8 状态不受本轮影响（D2 维护者裁定、declaration 归 09/33/34，均未触碰）。

### 8.4 AC9 判定与剩余缺口

- 真机 dedicated-server 冒烟（26.1.2）+ 本票从未跑过的 26.2.0(+fabric 双节点) 全套件均绿
  → AC9 勾选（按支持等级记录：26.1.2=真机冒烟+全套件、26.2.0=全套件、fabric 两节点=全套件
  （catalog/能力立场逐节点钉住）、1.21.1=本票先前全套件+本轮未复跑）。
- 剩余（显式记录，owner 票 34）：客户端侧 / PlayerEvents 真机路径；fabric 节点 runtime
  冒烟；26.2.0 真机冒烟。D4/D5 修复待维护者裁定（同 D2 通道）。
## 9. D4/D5 缺陷修复（2026-09-29，维护者裁定 FIX BOTH）

执行者：ticket-24-defects 修复轮（GLM-5.3 subagent worktree `../NekoJS-mult-t24fx`，
分支 `ticket-24-defects`，基于 mult@973defbc）。D4/D5 由 2026-09-28 真机冒烟轮发现
（记录在 `ticket-24-smoke-evidence` 分支的 §8/command-output/08，未随 mult HEAD 合并），
2026-09-29 维护者裁定两项均修。本轮首次在 production 主源码落改动。

### 9.1 D4：damagePre 取消静默 no-op（NeoForge）

- 根因：`LivingDamageEvent.Pre` 不实现 `ICancellableEvent`（21.1.227 / 26.1.2.71 /
  26.2.0.57 三版 sources 已核），`EventGroup.server` 的默认 predicate 把 DAMAGE_PRE 总线
  冻成不可取消，脚本 return true 被忽略。
- 修复：`EntityEvents.DAMAGE_PRE` 显式建可取消总线（`EventBusJS.of(type, true, dispatch)`
  与 D2/broken 同型）；`EventBusForgeBridge` 新增 `bindCancellable(bus, cancelAction)`
  ——post 返回真（脚本取消）时执行 cancelAction，绑定点把取消映射为
  `event.setNewDamage(0)`（伤害归零，原生伤害链走完：damagePost 仍以 0 触发）。
  不可取消总线绑入 `bindCancellable` 即抛 IAE，杜绝同类静默 no-op 复发。共享树单点
  覆盖 1.21.1/26.1.2/26.2.0（三版 API 同形）；fabric 侧 ALLOW_DAMAGE 反转接线本就
  正确，零改动（能力行已在 catalog-snapshot 更新语义注记）。
- 红→绿：新 `Ticket24DamagePreCancelSemanticsTest`（真家族总线：canCancel +
  取消短路可观察）修复前 2 用例红（canCancel=false 断言红 + 非 CancellableEventBus
  CCE）；`EventBusForgeBridgeCancelActionTest`（桥映射腿，red=缺重载的编译失败）。
- 真机：command-output/10（spider：取消 5 伤后血量 16.0 不变、damagePost
  healthDamage=0；对照 7 伤正常 16.0→9.0、healthDamage=7）。

### 9.2 D5：randomTick 对原版随机 tick 方块不可达（两 loader）

- 根因：两 loader 孪生 mixin 注入 `BlockBehaviour` 接口 default `randomTick`（空体），
  而 `BlockStateBase.randomTick` 虚分派到具体方块覆写——原版随机 tick 方块全部覆写
  （CropBlock/草方块等，1.21.1/26.1.2/26.2.0 三版 javap 实证），接口 default 永不执行。
- 修复：两孪生 mixin 改注入 `BlockBehaviour.BlockStateBase.randomTick(ServerLevel,
  BlockPos, RandomSource)` 漏斗（ServerLevel 逐位置虚分派的必经点，26.1.2 ServerLevel
  字节码 INVOKE 实证）；加 `isRandomlyTicking()` 守卫维持「仅自然随机 tick 方块」的
  文档语义（ServerLevel 对区段内任意位置都调漏斗）+ `hasListeners()` 高频短路。
  类名/json 注册不变（两 resources 根与 fabric json 均仍列原名）。
- 红→绿：新 `BlockRandomTickInjectionSiteTest`（反射实证漏斗形状 + 源 trace 断言两
  孪生注入漏斗并保留两守卫）——旧 mixin 下 source-trace 用例红，新 mixin 绿。
- 真机：command-output/10（forceload + `random_tick_speed 30`（26.x 改名已核）+ 草方块：
  秒级出 marker，35s 窗口 11150+ 次 grass_block 随机 tick；对照 2026-09-28 会话同设置
  0 marker）。wiki 示例的石头示例改为小麦（石头不 isRandomlyTicking，永不触发）。

### 9.3 表更与验证

- catalog-snapshot.md：damagePre 行注记更新为「取消=桥映射 setNewDamage(0)，damagePost
  仍以 0 触发；fabric 整体免除」（cancel 列本行本就记 true，修复后与运行面一致）；
  表头补显式可取消例外说明。
- 冻结测试两处随行为更新（有意变更）：`Ticket24GameplayEventCatalogTest` damagePre
  cancellable 期望加显式例外（与 D2/broken 同型）；PhaseTrace EntityEvents 由 13 个
  `.bind(` 改为 12+1（`.bindCancellable(`）。零 golden 文件改动。
- 验证：`:26.1.2:test` 456/0/0/58、`:1.21.1:test` 321/0/0/14、`:26.2.0:test` 456/0/0/58、
  `:26.1.2-fabric:test` 254/0/0/25、`:26.2.0-fabric:test` 254/0/0/25（--continue 全绿）、
  `guardLint` 绿、`:26.1.2:runServer` 真机 transcript（command-output/10，run 目录已清）。
  `:common:check` 未跑（common 模块零改动）。
- 与并行会话的边界：D2（BlockEvents.broken）修复是另一未提交工作，本分支不含、未触碰
  BlockEvents/BlockBrokenEventJS/FabricBlockEventBindings；Catalog/PhaseTrace 两测试的
  同行编辑为各自缺陷的独立必要更新，合并时语义叠加。
