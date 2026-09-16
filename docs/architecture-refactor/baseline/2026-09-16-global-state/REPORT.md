# 2026-09-16 global state：按类型 global、显式 shared 与候选顶层写集联合提交报告（ticket 10）

> 工单：`docs/architecture-refactor/implementation-tickets/10-global-state.md`（票面 Status/AC 勾选未动，关票由主会话负责）。
> 分支：`ticket-10-global-state`（worktree `D:/mcmodDemo/NekoJS/.worktrees/t10/NekoJS-mult`），基线 `9da3abf3`（认领提交）。
> 核心 spec：[`../../specs/10-shared-global-candidate-writes.md`](../../specs/10-shared-global-candidate-writes.md)（本票专属）；衔接 [05](../../specs/05-runtime-lifecycle-and-data.md)、[09](../../specs/09-reload-candidate-state-and-thread-contract.md)。
> 前置交付（消费不推翻）：ticket 05 `NekoRuntimeRoot`/`NekoRuntimeAssembly`、ticket 06 candidate/commit/`ReloadPhase`/pendingListeners、ticket 07 `ScriptLifecycleGate`/close 抢占/watchdog、ticket 09 managed surface 冻结面。
> 交付物：实现（`common/.../core/state/`）+ 18 个新测试（3 个 suite）+ 可执行示例（`examples/`）+ [MIGRATION.md](MIGRATION.md)。

**范围边界（工单明文，全程遵守）**：`shared` 是工作名（最终公开符号由 MANAGED_SURFACE 冻结，本票只同步 binding 定义与迁移表命名）；不实现领域计划（Item/Block modification 等只提供联合边界）；不新增权限系统、第二 runtime owner、网络同步协议、通用事务框架；不收紧高级 Java 访问；Node shim 不新增语言管线。

---

## 1. Commit 清单

| commit | 内容 |
|---|---|
| `7800fd97` | `feat(state)`: core.state 包（stores/store/generation/view/plan）+ `ReloadPhase.STATE_PLAN` + `BindingMembers.dynamicContainer` + factory/manager/root 接线 + 两 core plugin 摘除旧 `global` 绑定注册 |
| `c823c939` | `test(state)`: 逐 AC fixture（`Ticket10GlobalStateTest` 13 用例）+ 可执行示例（`GlobalStateExamplesTest` 4 用例 + `examples/`）+ 既有 harness 适配 |
| `4a00af8b` | **删除 commit（单独可 revert）**：`refactor(state)!` 删除 `NekoGlobal.java`（进程级静态 Map / 旧隐式跨类型回退），`NekoGlobalRemovalTest` 钉住删除 |
| `06e7b843` | `test(state)`: fixture 后清理共享 gameDir 脚本目录（顺序敏感 suite 保护）+ 共享节点测试树 `NetworkGenerationRoutingTest` 构造器适配 |
| 本提交 | `docs(baseline)`: 本报告 + MIGRATION.md |

未 push、未合并、未触碰 master 与其它 worktree。

## 2. 机制设计（与 05/06/07 的衔接）

### 2.1 root 拥有的状态域（`common/.../core/state/`，全部实例字段、零 static 可变状态）

| 类 | 职责 |
|---|---|
| `GlobalStateStores` | root 拥有（`NekoRuntimeRoot` 构造时创建，`globalState()` 公开 seam）。每 `ScriptType` 一个私有 store（`global:<type>`）+ 一个共享 store（`shared`，工作名）。全部 store 共用一把 root 级锁 → 「私有+共享」可在一次持锁内联合校验/联合发布。`closeAll()`（root close）释放；close 后获取 store 明确拒绝 |
| `GlobalStore` | 已提交 backing store：值 + 写入 generation + guest 标记三联条目；每 key 版本号 + store epoch（均在**已提交**变更时 bump）。Java 侧直接 put/remove/clear = 「其他 writer」公开 seam |
| `GenerationGlobals` | 每 generation 一个：事务（候选）/非事务（active、STARTUP reset+load、FILE 路径）两种形态；`preflightJoint()`/`publishJoint()`/`discard()`/`close()`；候选可挂 `CandidateStatePlan` |
| `GlobalView` | 绑定进 Context 的 `global`/`shared` 视图（实现 `Map`，成员读写删 = put/get/remove，interop 语义与被替换的宿主 Map 绑定一致——探针实证）。候选视图顶层写进写集（read-your-writes），active 视图直接提交 |
| `CandidateStatePlan` | 无领域语义的联合预检/联合成败边界（AC5）：`preflight()` 可自由抛（候选失败），`publish()` 契约化不得抛（违反则 global/shared 不发布、计划自身副作用不回滚） |
| `GuestValues` | guest 判定（见 §2.4 探针事实） |

### 2.2 reload 流程内的接入点（消费票 06 状态机，不另起炉灶）

```
reloadScriptsTransactional（票 06 既有）
 ├─ PREPARATION  createCandidateEnvironment → factory.newGeneration(type, transactional=true)
 │               （候选 global/shared 视图随环境创建，BINDING 阶段安装）
 ├─ BINDING      installEnvironmentBindings(ctx, type, globals)：putMember("global"/"shared", 视图)
 │               + bindingSchema 动态容器条目（preflight 对任意顶层 key 静默）
 ├─ EXECUTION    候选脚本顶层 set/delete/clear → 写集（read-your-writes；候选读已提交 store）
 ├─ EVENT_PLAN   （票 06 A1 前移，不变）
 ├─ STATE_PLAN   ★新增阶段：preflightJoint() = 私有写集校验 + 共享写集校验 + 外部计划 preflight。
 │               冲突 → reloadFailure(STATE_PLAN, domain=global-write-conflict)
 ├─ close 抢占检查（票 07，不变；候选丢弃时写集随之 discard）
 └─ COMMIT（票 06 单一 commit 点，新增步骤 0）：
      0. ★publishJoint()（root 锁内）：复验两写集（STATE_PLAN→commit 间闯入的 writer 在此
         被捕获——任何变更前抛出，active 完整保留）→ 外部计划 publish（契约不得抛）→
         一次性应用私有+共享写集（此后无失败路径，无半提交）
      1-5. 清扫旧监听器 → 发布新 runtime → 激活候选监听器 → 释放旧 module session →
           closeRuntimeResources(旧环境)（其内先 GenerationGlobals.close()：旧 generation
           写入的 guest 值按 generation 失效；非 guest 值保留在 root 级 store）
```

失败路径：`discardCandidate` 先 `globals.discard()`（写集+计划全弃）再关候选资源——顶层 set/delete/clear 均不污染旧 active。

### 2.3 进程级/generation 级所有权分界

- **root 级（跨 reload/server stop/切世界保留，root close 释放）**：私有 store、共享 store 的全部非 guest 条目 + 版本/epoch 记账。
- **generation 级**：视图绑定、候选写集、外部计划、以及「该 generation 写入的 guest 条目」（Context 销毁即失效，存入 Map 不获得永久保活）。
- 独立 root / 测试 runner 从空开始：`GlobalStateStores` 零 static（`Ticket10GlobalStateTest.stateDomainHasNoStaticStateAndNoSecondOwner` 反射钉住）。

### 2.4 Graal 实证事实（fixture 前置探针，已固化进代码注释）

1. polyglot 绑定成员遮蔽同名 globalThis 属性：`global.probeKey = x` 进绑定 Map，`globalThis.probeKey` undefined，`global !== globalThis`。旧 `NekoGlobal` Map 绑定与本次视图绑定行为一致——「容器接管 `global` 名」是既有事实，本票把它钉成契约（AC9 fixture）。
2. 脚本把对象/数组/函数存进宿主 Map 收到的是 `com.oracle.truffle.polyglot.PolyglotMap/PolyglotList/PolyglotMapAndFunction`（未被 relocation），宿主值是宿主类 → 类前缀判定 guest。
3. **`Value.asValue(v).getContext()` 在已进入（entered）Context 的线程上对宿主 String/Integer 也返回当前 Context**——不能用作 guest 判定（会把所有脚本写入误判为 guest、在 generation close 时误清）。首版实现即踩此坑，由 `candidateReadsYourWrites...` fixture 的失败暴露后改为类前缀判定。
4. `delete global.foo` → `Map.remove`、`global.clear()` → `Map.clear`、成员键 = Map 接口方法名（`Object.keys(global)` 同旧行为）——视图实现 `Map` 保持脚本表面逐点一致。
5. Context 关闭后对残留代理调 `asValue` 抛错 → guest 失效只读写入时记下的标记，绝不回碰代理。

## 3. 写集 / 冲突语义表

| 场景 | 行为 | 证据 |
|---|---|---|
| 候选顶层 `set` | 进写集；首写记录该 key 已提交版本基线；read-your-writes | `candidateReadsYourWritesAndSuccessfulCommitPublishes` |
| 候选顶层 `delete` | 进写集（remove 项，同上基线）；失败不发布 | 同上（`delete global.b` 分支） |
| 候选顶层 `clear` | 记录 store epoch 基线 + 作废 clear 前写集条目；clear 后的写入按「clear 后重写」发布 | 同上（`global.clear()` + `global.c = 3` 分支） |
| 成功 commit | `publishJoint` 锁内一次性发布（私有+共享+计划），无半提交 | 同上 + `candidateWritingBothGlobalAndSharedCommitsJointlyOrNotAtAll` |
| 失败/被杀/被抢占候选 | 写集全弃（discard），旧 active 值不变 | 同上（语句上限 kill 分支） |
| 其他 writer 改同管 key（候选期间） | STATE_PLAN 冲突 → 候选失败；**其他 writer 已提交值保留（不丢写）** | `otherWriterDuringCandidacyConflictsWithoutLosingTheOtherWriterValue` |
| clear 期间其他 writer 提交该 store | epoch 基线不符 → 同上冲突 | `GlobalStore.MapWriteSet.validate`（epoch 分支，被上表 clear fixture 覆盖语义） |
| 跨类型同名私有 key | 不同 store 各自版本 → 不冲突，两边成立 | `crossTypeSamePrivateKeyDoesNotConflict` |
| shared 竞争写入 | 共享 store 版本检出 → 候选失败，竞争方值保留 | `sharedCompetingWriteFailsTheCandidateAndKeepsTheCompetingValue` |
| STATE_PLAN 与 commit 之间闯入的 writer | commit 点锁内复验兜底（变更前抛出，active 完整） | `candidateStatePlanBoundaryJoinsJointPreflightAndJointOutcome` 第 4 段 |
| 外部计划 preflight 失败 | 候选失败，global/shared 全不发布 | 同上第 2 段 + AC4 失败方向 |
| 外部计划违反 publish 契约（抛出） | global/shared 不发布（reload 失败）；计划自身副作用不回滚（契约明示） | 同上第 3 段 |
| 嵌套对象/列表/已共享 Java 对象内部修改 | 不进写集、不承诺深回滚（同 generation 内立即生效；失败候选不回滚） | `nestedMutationsAreNotDeepRolledBack` |
| guest 函数/对象存入 Map | 写入方 generation 销毁即失效（条目清除）；宿主值保留跨 reload | `storedGuestFunctionsExpireWithTheirGenerationWhileHostValuesSurvive` |

## 4. AC1–AC11 逐条判定

| AC | 判定 | 证据 |
|---|---|---|
| AC1 同类型多文件共享同一 global；四类型同名 key 独立 | **pass** | `sameTypeFilesShareGlobalAndAllFourTypesReadBackIndependentValues`（真实 Graal 管线：SERVER 双文件 count 累加 11、CLIENT typeof server.count=undefined、四 store 读回各自值） |
| AC2 候选 read-your-writes；成功发布；失败时 set/delete/clear 均不污染 | **pass** | `candidateReadsYourWritesAndSuccessfulCommitPublishes`（含 clear+重写复合场景与语句上限 kill 失败路径；失败后 active 仍可再次 reload 恢复） |
| AC3 其他 writer 冲突检测、不丢写、候选失败；跨类型私有不冲突；shared 竞争失败 | **pass** | `otherWriterDuringCandidacyConflictsWithoutLosingTheOtherWriterValue`（phase=STATE_PLAN/domain=global-write-conflict）+ `crossTypeSamePrivateKeyDoesNotConflict` + `sharedCompetingWriteFailsTheCandidateAndKeepsTheCompetingValue` |
| AC4 一次候选同写 global+shared：联合成功或全部不发布 | **pass** | `candidateWritingBothGlobalAndSharedCommitsJointlyOrNotAtAll`（失败方向借外部计划 preflight 失败触发：两边均 null；成功方向两边均发布） |
| AC5 无领域语义的联合预检边界；测试/后续领域计划可挂入 | **pass** | `candidateStatePlanBoundaryJoinsJointPreflightAndJointOutcome`（pass/fail-preflight/publish-throw/锁内复验兜底四段）+ `CandidateStatePlan` javadoc 契约。本票未实现任何领域计划、未把领域 Adapter 拉进 global owner |
| AC6 最小可运行示例 + 迁移材料，只用已 gate 能力 | **pass** | `examples/`（6 个文件：same-type 1 + explicit-shared 2 + legacy-migration 2 + failure-retention 1）由 `GlobalStateExamplesTest` **原样拷进真实管线执行**（4 用例全绿）；[MIGRATION.md](MIGRATION.md) 覆盖同类型/显式 shared/旧跨类型迁移/失败保留四主题 |
| AC7 跨 reload/server stop/切世界保留；root close 释放；generation close 不误清；独立 root 从空开始 | **pass（server stop/切世界为 common 层最小模拟）** | `rootOwnedStateSurvivesReloadAndStopCycleAndIsReleasedByRootClose`（真实 `NekoRuntimeRoot`：空起步 → load → reload 保留累加 → `clearWorldPackListeners`+rediscover（平台 stop/切世界钩子的 manager 侧语义）仍保留 → root close 释放 → 第二个独立 root 从空开始且看不到旧 root 的 shared）。真机 server stop/切世界 in-game smoke 未跑（§6） |
| AC8 guest 函数/Value 不延长已销毁 Context 生命周期；不承诺深回滚 | **pass** | `storedGuestFunctionsExpireWithTheirGenerationWhileHostValuesSurvive`（候选期旧代 guest 仍可读=读已提交语义；commit 后失效；宿主值 7 保留）+ `nestedMutationsAreNotDeepRolledBack`（FILE 探针读回 99） |
| AC9 global 容器 vs globalThis 语言全局分工可外部观察；Node shim 用正确语言全局；不把容器当模块全局 | **pass（含一处既有事实的显式化）** | `globalIsTheStateContainerWhileGlobalThisStaysTheLanguageGlobal`（`global !== globalThis`、互不渗漏、`require`/`globalThis.__nekoNodeResolve` 走语言全局、写入落容器 store）。**既有事实显式化**：绑定安装会在 globalThis 上接管 `global` 属性名（`globalThis.global === global`）——与 1.2.0 前的 Map 绑定行为一致（探针对照），fixture 钉住为契约并写入 MIGRATION 表第 7 行；既有 Node shim 回归 `NodeModulesJsRegressionTest.nodeModuleSurfaceMatchesNodeSemantics`（shim 语境 `global === globalThis`）未改动、保持绿 |
| AC10 迁移表逐项；同类型不变；fixture 通过后删除旧隐式回退与双写 | **pass** | [MIGRATION.md](MIGRATION.md) §2 八行逐项映射；`sameTypeUsageUnchangedAndLegacyCrossTypeFallbackIsGone`（同类型跨 reload 累加 + CLIENT 读旧写法得 undefined）；删除在独立 revertable commit `4a00af8b`（删除时全部 fixture 已绿），`NekoGlobalRemovalTest` 钉住类不复活；无双写（视图安装点唯一，插件注册行已摘除） |
| AC11 不新增权限系统/第二 owner/网络协议/通用事务框架；不收紧高级 Java 访问 | **pass（结构性证据）** | 新增类型为最小状态域实现（Map 语义 + 版本计数 + 计划接口），无注解/权限/网络/事务框架面；`stateDomainHasNoStaticStateAndNoSecondOwner` 钉住零可变 static；`guardLint` 0 警告（守卫块 257/扫描 409 文件）；高级 Java 面 diff 为零（`AdvancedJavaInteropSmokeTest` 随 `:common:check` 绿） |

## 5. 验证证据（命令 + 结果，全部实跑）

| 命令 | 结果 |
|---|---|
| `./gradlew :common:check :common-api-processor:test --rerun-tasks --console=plain` | **绿**：:common 1496 tests / 0 failed / 0 errors / 4 skipped（本票 +18：`Ticket10GlobalStateTest` 13 + `GlobalStateExamplesTest` 4 + `NekoGlobalRemovalTest` 1）；processor 13 tests / 0 failed |
| `:common:test --rerun-tasks` ×3（顺序稳定性复核） | 3 次全绿（共享 gameDir 顺序敏感 suite 曾因 fixture 残留偶发红，已加 @AfterEach 清理后稳定） |
| `./gradlew guardLint --rerun-tasks --console=plain` | **绿**：守卫块 257，扫描 409 文件；豁免 0；警告 0 |
| `./gradlew :26.1.2:build --rerun-tasks --console=plain` | **绿**（250 tests / 0 failed / 36 skipped，含共享测试树 `NetworkGenerationRoutingTest` 适配后） |
| `./gradlew :1.21.1:build --console=plain` | **绿**（改动了共享树 `src/main` 与 `src/fabric`，按工单加跑） |
| `./gradlew :26.2.0:compileJava :26.1.2-fabric:compileJava :26.2.0-fabric:compileJava --console=plain` | **绿**（26.2.0 与两 fabric 节点的编译面；五节点全量 build 由主会话合并后统一跑） |

复现（定向）：

```bash
./gradlew :common:test --tests "com.tkisor.nekojs.core.state.Ticket10GlobalStateTest" --console=plain
./gradlew :common:test --tests "com.tkisor.nekojs.core.state.GlobalStateExamplesTest" --console=plain
./gradlew :common:test --tests "com.tkisor.nekojs.core.state.NekoGlobalRemovalTest" --console=plain
```

## 6. not-verified 与偏离说明（给 reviewer）

| # | 项 | 状态 / 取舍 | owner |
|---|---|---|---|
| N1 | 真机 server stop / 切世界 / CLIENT 真端的 in-game smoke | common 层以 `clearWorldPackListeners`+rediscover 模拟平台钩子的 manager 侧语义（真机生命周期由平台 entry 触发，与 05/06 报告的 CLIENT not-verified 同源）；可用 minecraft-mcp 补一轮 `/nekojs reload` + stop/重开世界的 smoke | 主会话 / minecraft-mcp |
| N2 | `globalThis.global` 接管为容器（AC9） | **既有事实的显式化而非新行为**：1.2.0 前的 `putMember("global", Map)` 同样在 globalThis 上定义该属性（探针对照证明）；本票不「修复」它（改回语言全局会让裸标识符 `global` 解析到 globalThis，容器绑定失效——探针实测赋回后 `global.x` 不再进 Map）。已钉入 fixture + MIGRATION 表第 7 行，Node 模块内部继续用 `globalThis.*`。若维护者希望 Node 语境 `global` 恒等 globalThis，需要语言管线决策（本票明文不新增语言管线） | 维护者裁决（可选后续票） |
| N3 | 冲突粒度 = 顶层 key 版本 + store epoch | spec 10 把「具体版本检测与实现留给 W1/W4」；本票实现为首写基线 + clear-epoch。已知边界：候选只**读**不写的 key 不产生冲突检测（读不加锁，spec 未要求）；候选 A 与候选 B（不同类型）并发提交 shared 同 key 时先提交者胜、后者锁内复验失败（确定性正确，未做专门双候选并发 fixture——生产同类型串行、跨类型真并发窗口极窄） | 记录在案；如需双候选并发 fixture 归后续 hardening |
| N4 | 候选执行期间旧 generation 的 guest 值对候选可读 | 读已提交语义的自然结果（旧代 commit 前仍存活），fixture 显式钉住；与「guest 按 generation 失效」不矛盾（失效发生在旧代关闭=commit 点） | — |
| N5 | `ScriptManager.reloadScriptFile`（FILE 路径）与 STARTUP reset+load 直接提交 | 非事务路径沿用票 06 的显式标记（`nonTransactional()`）；其顶层写经 active 视图直接发布，与 FILE/STARTUP 非候选语义一致（spec 未要求 FILE 事务化） | — |
| N6 | 嵌套深回滚/共享 Java 对象内部修改 | 明确不承诺（spec 10 Out of Scope）；fixture 钉住「不回滚」行为本身 | — |
| N7 | probe/declaration 面 | `global` 不再经插件 binding 注册 → legacy catalog（观察面）不再含 `global` 条目；probe golden 全绿（`legacy-bindings.expected.d.ts` 用合成 fixture，不受影响）。`shared` 的 declaration/contract 注册归 MANAGED_SURFACE（W5 冻结名字后同步 binding 定义与迁移表，票内明文不阻塞） | W5/MANAGED_SURFACE 组 |
| N8 | 测试 gameDir 仍用 common 既有固定目录模式 | common 测试树无法访问 `src/test/.../TestGameDirs`（节点树专属）；沿用 common 既有 `TestPlatformInit` 先例，并以 @BeforeEach+@AfterEach 双清+3×rerun 稳定性复核补偿。若 common 也要求 unique gameDir，可把 helper 下沉 common testfixture（未在本票扩面） | 维护者裁量 |
| N9 | `ReloadProgressTracker` 总步数 5→6 | STATE_PLAN 新增一步的机械适配；HUD 展示无契约断言 | — |

## 7. 给合并协调者的注记

- 删除 commit `4a00af8b` 可独立 revert（revert 后 `NekoGlobalRemovalTest` 会红——那是刻意设计：复活旧 Map 必须过一次显式决策）。
- 本票触碰 `src/main/java/com/tkisor/nekojs/core/NekoJSCorePlugin.java`、`src/fabric/java/com/tkisor/nekojs/fabric/FabricCorePlugin.java`、`src/test/java/com/tkisor/nekojs/network/NetworkGenerationRoutingTest.java`（共享树/共享测试树/fabric 源根）——与票 31 源根迁移、票 18 等触碰同文件簇的票存在潜在文本冲突，合并时留意。
- `ScriptBindingSchema.BindingMembers` record 加了第三个 canonical 组件 `dynamicMembers`（带兼容构造器）——下游若有解构该 record 的代码需注意（仓内无此用法，编译全绿）。

## 8. 双轴审查整改记录（reviewer 判定：需修复后合并 → 已整改）

| # | 级别 | finding | 整改 |
|---|---|---|---|
| F1 | 必修 | `preflightJoint()` 的 `validate()` 无锁读共享可变状态（keyVersions HashMap + 非 volatile epoch 与并发 writer 的 bump 构成 JMM 数据竞争；commit 点锁内复验是权威兜底但预检自身不得裸读） | 两个 `validate()` 包进 `synchronized (stores.lock)`；外部计划 preflight 保持锁外（javadoc 说明该约束） |
| F2 | 优化 | AC7 server stop 腿 fixture 传空 WORLD 列表近乎恒真 | 如实批注：该腿实际支撑 = 结构性论证（closeAll 全仓唯一调用点 NekoRuntimeRoot.closeSilently），真机 smoke 归 N1 |
| F3 | 优化 | `MapWriteSet.apply()` 发布后不清写集账目（hasWrites 仍 true，状态机不自描述） | apply 末尾清 ops/cleared/clearBaseEpoch，写集一次性消费 |
| F4 | 优化 | commit 点 publishJoint 失败报 STATE_PLAN 与失败发生处（COMMIT）不一致 | ScriptManager 归因口径注释显式化（按「候选期状态计划冲突」归类、domain 消歧） |
| F5 | 优化 | REPORT 称 examples 5 个文件（实际 6 个） | 计数更正并按主题分列 |
| F6 | 优化 | common 测试固定 gameDir（TestPlatformInit）与节点树 TestGameDirs 不一致 | 维持 N8 登记（common 测试基建改动超出本票面；helper 下沉 common testfixture 归后续票） |
