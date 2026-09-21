# 票 26 证据报告：CLIENT 输入与 HUD callback 生命周期

日期：2026-09-21
Worktree：`D:/mcmodDemo/NekoJS-mult-t26`（分支 `ticket-26-client-input-hud`，基线 `f9c725f0`，含 cherry-pick `4a9ed9c8`）
执行者：zed-flash-26（deepseek-v4.1-flash subagent worktree）

## 1. 范围

验证并收口**既有** CLIENT 输入 / HUD callback 生命周期，**不新增事件**：

- `KeyBindEvents.pressed/released/tick`（按绑定 id 定向的输入事件）、`ClientEvents.hud`（每帧 HUD 监听）、
  `ClientEvents.registerKeyMappings`（原生 key mapping 注册事件）复用各自既有总线；
- `KeyBindEvents.register(...)` 与 `ClientEvents.hudRender(...)` 是**直接注册入口**（调用即注册），
  不得被误写成事件监听，也不得改成监听；
- 收口 `ClientEvents.hudRender` / `worldRender` 注册面的 **generation 可见性**：候选期 inert、
  commit 点整批换装、失败/取消保留旧 active。

不在本票范围：GUI/Screen 与 render Adapter 资源（票 27）、PostEffects 声明生命周期（票 28）、
Assets/lang 生成（票 29）；Fabric 侧不补 parity（如实记 unavailable/partial）。

## 2. 实现摘要

### 2.1 修复的真实缺陷（本票主要产出）

**缺陷链路（两条路径叠加，均违反 AC4）**

1. `RenderRegistrationBusJS.execute()`（26.x 与 1.21.1 共享树）在脚本**执行期**直接调用
   `ClientRenderRegistry.registerHud/registerWorld`，写入**进程级 static** `HUD_RENDERERS` /
   `WORLD_RENDERERS` 表。于是候选 generation 的脚本一执行，其渲染器**立即上生产路由**：
   同一帧的 `RenderGuiEvent.Pre/Post` 就会派发候选闭包（候选期不可见性被破坏）。
2. 与此同时 `ClientRenderPlugin.beforeScriptsLoaded(ScriptType.CLIENT)` 调
   `ClientRenderRegistry.clearAll()`，而 `ScriptManager.doLoadScripts`/`reloadScriptsTransactional`
   的调用位置在**候选脚本执行之前**（`ScriptManager.java:817`）。因此候选期的一开局就把
   **旧 active 的渲染器整表清空**——一旦该候选失败/取消，旧 generation 已经无法继续服务
   （失败保留旧 active 被破坏）。

两条路径叠加的净效果：候选期既能看到候选的渲染器，又会丢掉旧 generation 的渲染器——
与 spec 09「候选期不可见 + 失败保留旧 active」完全相反。

**发现过程（可复现）**

- `:26.1.2:test` 全量绿（330/0/54）**不能**发现它：该域没有覆盖候选可见性的 fixture。
- 在实现修复后写 Lifecycle fixture，第 ③ 个用例
  `aGenerationThatRegistersNothingRetiresThePreviousRenderers` 红：
  `the fast path agrees: the layer has no renderer left ==> expected: <true> but was: <false>`。
  同时 `dispatchHud(...).isEmpty()` **通过**——两个口径矛盾。追查后确认：由于销毁的旧 Context
  在 `isContextDead` 口径下不算「死」（见 §6.3），`isEmpty()` 只是「回调抛错被 `invokeEntry`
  的 catch 吞掉」造成的**假绿**，条目其实还在生产表里。
- 根因：候选计划是**惰性**建立的（首次 `registerHud/registerWorld` 才 `new Candidate` 并
  `registerPlan`）。因此「本代一个渲染器都不注册」时**计划从不注册 → `publish()` 从不执行 →
  生产表从不换装**，旧的 `'first'` 条目原封不动。这违反
  `CandidateDomainCollector` 的既有契约：「无监听器时也应注册计划（空计划）——声明移除的
  『恢复基线』语义依赖空计划参与 commit」（`common/.../core/lifecycle/CandidateDomainCollector.java:28-29`）。
  票 22、票 28 的收集器都遵守了该契约（票 28 的 `collect` 无论如何都 `handle.registerPlan(plan)`）。

**修复（复用既有接缝，不新增框架）**

| 文件 | 作用 |
|---|---|
| `src/.../client/render/ClientRenderRegistry.java` | 注册路由 `register()`：先 `collectIntoCandidate()`，不是候选才写生产表。首次候选注册经既有接缝 `ScriptManager.registerCandidatePlan(context, plan)` 把 inert 批次挂上候选的联合预检/发布边界；新增 `registerCandidateBatch(Context, Handle)`（**无条件**注册，空批次也注册）；新增非事务路径 generation 认领 `beginActiveGeneration(context)`（初始加载/击杀重建换新 Context → 换装整表；单文件 reload 复用同一 active Context → 不再误清同代渲染器）；批次地图剪枝保证最多一个在途候选；`Candidate implements CandidateStatePlan`，`publish()` 在 commit 点整批 `clear+putAll` 并把 `activeContext` 设为候选 Context |
| `src/.../client/render/ClientRenderDomainOwner.java`（新增） | `CandidateDomainCollector`：`domain=client-render-registration`、`scriptType=CLIENT`；`collect()` 委托 `registerCandidateBatch(handle.candidateContext(), handle)`。收集无条件发生，**空批次也参与 commit** |
| `src/.../NekoJSMod.java` | `registerClient` 的 client dist 分支与 `PostEffectDomainOwner` 并列 `root.registerDomainCollector(new ClientRenderDomainOwner())` |
| `src/.../client/render/ClientRenderPlugin.java` | **删除**：去掉 `beforeScriptsLoaded` 的 `clearAll()` 后该类已无任何职责（唯一方法就是那次清空；不注册事件组/绑定/hook）。全仓引用为 0（见 §4 命令 9） |

**修复后的两条路径**

- **事务路径（CLIENT reload）**：候选脚本执行期 `hudRender(...)` → 进该候选的 inert 批次
  （生产表不动）→ DOMAIN_PLAN 阶段 `ClientRenderDomainOwner.collect` 把**同一批次实例**
  挂上联合边界（`registered` 标志去重，不会重复注册）→ STATE_PLAN 联合预检（本批无可拒绝
  条件；payload 校验仍在 `RenderRegistrationBusJS.execute` 收集点当场失败）→ commit 点
  `publish()` 整批换装，旧代未声明的 id 随 `clear` 退役 → **失败/取消时批次不会被发布（`publish`
  不执行），旧 active 原样继续服务**。
  **如实限定**：失败批次的 `Candidate` 对象**不在失败当刻从 `CANDIDATE_BATCHES` 移除**——
  prune 只发生在「下一轮候选的首个注册」或「下一轮 DOMAIN_PLAN 收集」（`collectIntoCandidate`/
  `registerCandidateBatch` 的 `removeIf`）。因此失败后到下一轮候选之间，map 里会短暂保留一个
  以已销毁 Context 为 key 的批次（含其 Graal `Value`）。功能上无影响（`dispatch`/`hasHud` 只走
  生产表），但 AC4 字面的「候选资源全部清理」并未在失败当刻兑现，也**没有**直接断言；已有证据
  是间接的（下一轮仍能正常 commit）。此处按实修正原表述。
- **非事务路径（初始加载 / 击杀重建 / 单文件 reload）**：没有候选/commit，由
  `beginActiveGeneration(context)` 在「首个来自新 Context 的注册」时换装整表。初始加载与
  击杀重建各有一个新 Context（脚本以同一批来源重跑），因此换装语义正确；单文件 reload
  复用当前 active Context，因此**不会**清掉同代其它渲染器（这是顺带修掉的旧隐患）。

### 2.2 未改动的面（明确）

- `KeyBindEvents.register(...)` 的**同 id 幂等 + 绑定跨 CLIENT reload 存活**是既有 javadoc
  明写的裁定语义，本票未改（AC2 也要求幂等）。
- `pressed/released/tick` 的监听器继续走 `EventBusJS` 的 `PendingListener`（EVENT_PLAN 阶段），
  天然 generation-scoped，无需改动。
- `ClientRenderRegistry` 的 public 面（`registerHud/registerWorld/unregisterHud/unregisterWorld/
  hasHud/hasWorld/clearAll/dispatchHud/dispatchWorld`）签名与行为保持；`clearAll()` 保留 public
  （诊断/测试用），语义变化见 MIGRATION §2。
- 1.21.1 侧：`ClientRenderRegistry`/`RenderRegistrationBusJS` 是**共享树**文件（版本守卫外），
  本修复同样作用于 1.21.1；`versions/1.21.1` 侧无 `ClientRenderPlugin` 孪生文件，故无成对改动。

## 3. 逐条 AC 判定

| AC | 判定 | 证据 |
|---|---|---|
| AC1 CLIENT session 由唯一 Runtime Root 在客户端启动入口创建；早期 key mapping、client tick 与 timer flush 同一 owner；dedicated server / 裸 JVM 不加载 client-only 类 | **满足** | `ClientEventsPoint.POINT` 为 `.clientOnly()`（`common/.../core/plugin/ClientEventsPoint.java:41`），收集器产物回退为 events 产物；`NekoJSMod.registerClient` 先判 `McPlatformCompat.get().isClientDist()` 才注册收集器与 `NekoJSClient.register`；`NekoJSClient.onClientSetup`（`FMLConstructModEvent`，早于 `RegisterKeyMappingsEvent`）→ `loadScripts()`，`onClientTickPost` → `flushReadyNodeTimers()`，同一 Client 线程；`ClientRenderDomainOwner.scriptType()==CLIENT`、`PostEffectDomainOwner` 同款。命令 1/2（26.1.2 全量绿）证明 client-only 类在节点测试 JVM 中正常初始化。**边界**：dedicated server / 裸 JVM 的「不加载」属类加载器事实，本票未新增 fixture 直接断言（见 §5） |
| AC2 keybind 注册在正确注册期执行且同 full id 幂等，reload 不重复 KeyMapping；pressed/released/tick 只派发给新 generation；非法 id/key/category 显式失败 | **满足** | 幂等：`KeyBindEvents.registerBinding` 先查 `BINDINGS.get(fullId)` 返回既有 mapping，`KeyBindEventsTest.registerBindingIsIdempotentAndNamesTheMapping` 断言 `assertSame(first, second)`；注册期：`RegisterKeyMappingsEvent` 窗口内走 `event.register`，窗口外追加 `options.keyMappings`（`KeyBindEvents.java:194-242`）；新 generation：Lifecycle `candidateRegistrationsAreInvisibleUntilCommitThenSwapExactlyOnce` + `keyBindListenersFollowTheSameCandidateBoundaryWithoutDuplication`。**如实限定**：keybind 用例断言的是 `PRESSED.registeredKeys()`（`EventBusJS:281` 返回 `Set<KEY>`），同一 id 的重复监听会**塌缩**，故此断言只能证明「key 集合不变」，**不能**检测同 id 重复注册（「不重复」是本行原表述的过度声称，已删）；且 `pressed/released/tick` 的「只派发给新 generation」**无 `post()` 派发断言**——三条触发总线的 generation-scoped 派发本票未直接验证（HUD/渲染器侧才是本票真实覆盖的面）；非法输入：`KeyBindEventsTest.invalidIdentifiersFailWithActionableError`/`keyParsingAcceptsVanillaNamesAndNull` 断言错误消息含输入原文，`RegisterBus.execute` 对参数个数/非字符串/空 id 抛 `IllegalArgumentException` |
| AC3 小驼峰脚本名；组名 `KeyBindEvents`/`ClientEvents`；pressed/released/tick 与 registerKeyMappings/hud 复用既有事件；register/hudRender 保留直接注册语义；声明/示例/catalog 区分注册入口与监听；无第二 bus | **满足** | `Ticket26ClientInputHudSurfaceTest`（4 tests）：`scriptVisibleMemberNamesAreLowerCamelCase`（正则钉 `[a-z][A-Za-z0-9]*`）、`registrationEntriesAreNotListenerBuses`（`REGISTER instanceof RegisterBus`、`HUD_RENDER instanceof RenderRegistrationBusJS`、`HUD` 是 `PainterJS` 普通监听、三个触发总线 `canDispatch()==true`）、`productionRegistrationExposesTheDocumentedMembersOnce`（经 `NekoJSCorePlugin.registerEvents/registerClientEvents` 构造真实 registry，成员集恰 `{pressed,released,tick,register}`，`assertSame` 钉字段单例，`scriptType()==CLIENT`）、`inputHudSurfaceDoesNotDuplicateOtherDomains`（跨组 bus identity 唯一、无 `PostEffect*`/`AssetsEvents` 第二套域）；示例见本目录 `examples/client-input-hud.js` |
| AC4 候选期新 listener/renderer 对生产路由不可见；commit 后旧 listener/timer/handler 停止接收、新 generation 恰好一次；失败或取消时旧 active 继续可用且候选资源清理 | **部分满足（本票修复项）**：前三半满足、末半「候选资源全部清理」未在失败当刻兑现 | `Ticket26ClientInputHudLifecycleTest`（4 tests）：`candidateRegistrationsAreInvisibleUntilCommitThenSwapExactlyOnce`（DOMAIN_PLAN 阶段 peer 观察到生产路由仍是旧 `['first']`；commit 后恰 `['second']`）、`aRejectedCandidateKeepsServingThePreviousActiveRenderer`（STATE_PLAN 拒绝整批 → `dispatchHud` 仍 `['first']`、`hasHud` 仍 true、下一轮仍能提交＝无挂起批次泄漏）、`aGenerationThatRegistersNothingRetiresThePreviousRenderers`（空批次参与 commit → 旧渲染器真退役，`dispatch` 与 `hasHud` 两口径一致）、`keyBindListenersFollowTheSameCandidateBoundaryWithoutDuplication` |
| AC5 按键状态、consumeClick 与 HUD 呈现结果可从脚本调用者 Interface 观察；断言不依赖 KeyMapping 私有集合或平台回调对象身份 | **部分满足** | 已满足部分：`KeyBindEvents.register` 返回 `KeyMapping` 句柄（`KeyBindEventJS.getKeyMapping()` 透传同一实例），`KeyBindEventsTest.registerBindingIsIdempotentAndNamesTheMapping` 断言 `assertSame` 与 `first.isDown()`、`getName()`、`saveString()`（公开面）；HUD 呈现结果经 `dispatchHud` 的**调用探针**观察（脚本回调写 `ctx.add(id)`），不读私有注册表、不断言回调对象身份。**缺口**：未新增「脚本侧 `consumeClick()` 改变 `isDown` 观测」的 fixture（无头 JVM 无真实按键状态源，需真机或注入 `KeyMapping` 状态），见 §5 |
| AC6 平台/版本 Adapter 按各节点既定支持等级与声明能力验证真实差异；不可用时 supported/partial/unavailable 明示，不自动补 Fabric parity | **部分满足（不勾选）** | 共享树事实：`ClientRenderRegistry`/`RenderRegistrationBusJS`/`ClientEvents` 为 26.x+1.21.1 共享，`versions/1.21.1` 仅有 `ClientRenderEvents`/`HudRenderContextJS`/`WorldRenderContextJS` 三个 override（层分发差异），无 `ClientRenderPlugin` 孪生；`KeyBindEvents` 在 fabric 有独立孪生（`KeyMappingHelper` 时机差异），`versions/1.21.1` 无该域（`KeyBindIds` 为 `>=26` 守卫）。**缺口**：未跑 `:1.21.1:test` 与 fabric 节点 test（本票验证矩阵只跑 26.1.2 + common），且**未新造** capability 矩阵条目，正式按节点记录归 fabric/节点 owner，见 §5 |
| AC7 调用者 Interface、Adapter 契约、runtime member、TS/Python declaration、contract/golden 与节点 runtime smoke 可互相追溯；普通测试只读 golden | **部分满足（declaration 面不勾选）** | 已交付：runtime member 反射 fixture（`Ticket26ClientInputHudSurfaceTest`，经生产注册路径读 catalog metadata 与字段单例）；事件成员进入 ticket-33 跨节点只读基线 `src/test/resources/nekojs/platform-gates/event-surface-domains.txt`（`KeyBindEvents | 26.1.2 = present | buses=pressed,register,released,tick`、`ClientEvents ... hud,hudRender,registerKeyMappings`），`:26.1.2:platformGateTest` 绿。**缺口（实证）**：命令 8 的 grep 显示 `api-manifest-core.json`、三个 probe `*.expected.d.ts`、`declaration-parity.txt` 对本票域（`KeyBind|hudRender|registerKeyMappings|KeyMapping`）**全部 0 命中**——与票 28 AC7 同类缺口，claim record 承诺的 TS/Python declaration 未兑现；本票**未**新造 declaration 条目，也**未**做节点 runtime smoke（真机 HUD 出图），故 AC7 不勾选 |
| AC8 GUI、render Adapter、PostEffects 与 Assets 保持独立 owner；本票不重复声明或清理他们的资源 | **满足** | `Ticket26ClientInputHudSurfaceTest.inputHudSurfaceDoesNotDuplicateOtherDomains`：跨组 bus identity 唯一；不存在 `PostEffectEvents`/`AssetsEvents` 组、无 `PostEffect*` 前缀组。`ClientRenderDomainOwner` **只**管 HUD/世界渲染器注册（不触碰 `PostEffectManager`、不触碰 `DataGeneratorJS`/`LangGeneratorJS`）；`ClientEvents.POST_EFFECTS`/`GENERATE_ASSETS`/`LANG` 成员未被本票修改（`ClientEvents.java` 本票 diff 为 0 行） |
| AC9 旧输入/HUD 入口、重复 handler 或绕过 Runtime Root 的静态装配只能在替代路径 parity、公开迁移表、旧 route 无消费者和维护者确认后删除；不保留长期双路径 | **不勾选（维护者 sign-off 门禁）** | 本票**确实删除了一个旧装配点**（`ClientRenderPlugin` 的「load 前整表清空」路径，见 MIGRATION §1/§3），替代路径 parity、迁移表与无消费者证据均已在 §4 命令 9 与 MIGRATION 备齐；但该删除改变 CLIENT reload 的资源释放时机（调用者可见副作用：不再有「加载前清空」），按票据 Human input note，**不等同于勾选**，待维护者确认 |

## 4. 真实验证命令与结果

所有命令在本 worktree 真跑；原始输出在 `command-output/`。

| # | 命令 | 结果 |
|---|---|---|
| 1 | `gradlew :26.1.2:compileJava --console=plain` | BUILD SUCCESSFUL in 19s（仅既有 deprecation/this-escape 提示）→ `01-compileJava.txt` |
| 2 | `gradlew :26.1.2:test --console=plain` | BUILD SUCCESSFUL in 21s；tests=330 failures=0 errors=0 skipped=54 → `02-26.1.2-test.txt` |
| 3 | `gradlew :26.1.2:test --tests '*Ticket26ClientInputHudLifecycleTest*' --rerun`（修复前） | **BUILD FAILED**；tests=4 failures=3（③ 的真红：`expected: <true> but was: <false>`）→ `03-ticket26-red.txt` |
| 4 | 同上（修复后） | BUILD SUCCESSFUL in 9s；tests=4 failures=0 → `04-ticket26-green.txt` |
| 5 | `gradlew :26.1.2:test --tests '*Ticket26*' --rerun --console=plain` | BUILD SUCCESSFUL in 12s；Lifecycle 4/0/0、Surface 4/0/0 → `05-ticket26-selection.txt` |
| 6 | `gradlew :26.1.2:platformGateTest --rerun --console=plain` | BUILD SUCCESSFUL in 5s；EventSurfaceDomainGateTest 1/0/0、PlatformSpecContractGateTest 1/0/0，**无 member-drift**（基线未改）→ `06-platformGate.txt` |
| 7 | `gradlew :common:check --rerun-tasks --console=plain` | BUILD SUCCESSFUL in 3m25s（含 checkCommonIsolation）；common tests=1756 failures=0 errors=0 skipped=4 → `07-common-check.txt` |
| 8 | declaration 覆盖 grep（见文件） | 5 个 declaration golden 对本票域 0 命中 → `08-declaration-coverage.txt` |
| 9 | 旧 route / 被删组件消费者 grep | `ClientRenderPlugin` 引用 0（仅自身 + 历史日志）；`clearAll` 生产调用点 0 → `09-old-route-consumers.txt` |
| 10 | `gradlew guardLint --console=plain` | BUILD SUCCESSFUL in 4s；守卫块 291、扫描 440 个文件、超限豁免 0、警告 0 → `10-guardLint.txt` |

**红→绿证据原文（③，AC4 的核心）**

```
--- 修复前 ---
aGenerationThatRegistersNothingRetiresThePreviousRenderers() FAILED
org.opentest4j.AssertionFailedError: the fast path agrees: the layer has no renderer left
  ==> expected: <true> but was: <false>
  at ...Ticket26ClientInputHudLifecycleTest.aGenerationThatRegistersNothingRetiresThePreviousRenderers(Ticket26ClientInputHudLifecycleTest.java:152)

--- 修复后 ---
tests=4 failures=0 errors=0
OK: aGenerationThatRegistersNothingRetiresThePreviousRenderers()
```

## 5. 未验证项与 owner

| 项 | 状态 | owner / 建议 |
|---|---|---|
| TS/Python declaration 面（AC7） | 未覆盖：`api-manifest-core.json` 与 probe `*.expected.d.ts` 对本票域 0 命中 | Managed Surface/Probe owner（与票 28 AC7 同一缺口，应统一批次处理） |
| 节点 runtime smoke（真机 HUD 实际出图、按键真实触发） | 未做：无头 JVM 无法驱动 `RenderGuiEvent`/`KeyMapping` 真实状态 | 票 34 P4 真机试做 |
| `:1.21.1:test`、`:26.1.2-fabric:test`、`:26.2.0:test` | 未跑（本票验证矩阵只覆盖 26.1.2 + common） | 主会话按需补跑；共享树改动对 1.21.1 生效，fabric 侧 `KeyBindEvents` 为独立孪生（未改） |
| dedicated server / 裸 JVM「不加载 client-only 类」的直接 fixture | 未新增；现状由 `ClientEventsPoint.clientOnly()` + `NekoJSMod` dist 判定承载 | 若要机检需在无 client dist 的测试 JVM 断言类不可加载 |
| capability 矩阵中本域的 supported/partial/unavailable 条目 | 未新造 | fabric/节点 owner（票 31/32） |
| 脚本侧 `consumeClick()` 改变状态的端到端断言 | 未做（无头无真实按键源） | 真机 smoke（票 34）同批 |

## 6. 双轴审查（`/code-review` 口径）自查

### 6.1 标准轴（AGENTS.md / docs/agents/coding.md）

| 检查项 | 结论 |
|---|---|
| 修改前读实现、调用者与测试 | 已做：读 `RenderRegistrationBusJS`/`ClientRenderRegistry`/`ClientRenderPlugin`/`ClientRenderEvents`/`NekoJSMod`/`ScriptManager`（候选/commit 阶段）/`CandidateDomainCollector`/`CandidateStatePlan`/`GenerationGlobals` |
| 复用既有机制、不加投机抽象 | 复用 `CandidateDomainCollector` + `ScriptManager.registerCandidatePlan` 既有接缝；未新增第二 runtime owner / 第二事件 bus / 第二 registry path |
| 复现根因而非症状 | 是：修的是「空批次从不参与 commit」契约违背 + 「候选期直写生产表」，不是给测试放宽断言 |
| 注释与实现一致 | 删除的 `ClientRenderPlugin` 类注释已随文件删除；`ClientRenderRegistry` 类注释里「reload 前由 ClientRenderPlugin#beforeScriptsLoaded 整表清空」这一失效描述已改写；新增注释描述的 `registerCandidateBatch`/`publish`/`beginActiveGeneration` 均已核实为真实方法 |
| 日志级别与内容 | 本票未新增日志；既有 `invokeEntry` 的 `ScriptErrorReporter` 记录路径未改 |
| 不静默 no-op | 是：空批次走 commit（退役）而不是「什么都不做」；本域 payload 非法仍在收集点抛 `IllegalArgumentException` |
| 无恒真断言 | 已逐条自检：无 `assertSame(x,x)`、无对字面量集合自断言；`SurfaceTest` 的 `assertSame` 读真实字段单例，`Lifecycle` 的断言读探针实际调用结果 |
| 不为了让测试变绿而放宽断言 | 已遵守：③ 修复前红，处理方式是**改生产代码**而非改断言；②④ 的断言修改是因为原断言错误（peer 只拒绝一次 / 候选期生产路由服务旧代），修改后仍是真断言 |
| golden / 基线 | 本票**未改动任何 golden**（`event-surface-domains.txt` 未动，`platformGateTest` 无 drift） |
| guardLint / 版本守卫 | 未新增/修改版本守卫（`KeyBindIds` 的 `>=26`、`NekoJSMod` 的 neoforge 守卫保持）；`guardLint` **已跑**：BUILD SUCCESSFUL（守卫块 291、扫描 440 文件、超限豁免 0、警告 0），见 §4 命令 10 |
| 中文注释沿邻近风格 | 沿用邻近先例（`ClientRenderRegistry`/`PostEffectDomainOwner` 中文注释）；未做批量语言重写 |

### 6.2 规格轴（AC 是否真的满足 / 有无超范围）

- **AC 满足**：AC1/2/3/4/8 满足（AC4 为本票修复项，有红→绿证据）；AC5/6/7 如实降级为部分满足/不勾选；AC9 保持未勾选（维护者 sign-off）。
- **范围**：本票只碰 CLIENT 输入/HUD 域文件（`bindings/event/client/*`、`client/render/*`、`NekoJSMod` 的 client dist 分支、`KeyBindEvents` 未改绑定语义、`ClientEvents.java` **0 行 diff**）。未触碰票 27（GUI/render Adapter 资源）、票 28（PostEffects）、票 29（Assets）的 owner 资源；未改共享 golden。
- **做票据没要求的事？** 一处需要维护者知情的额外改动：删除 `ClientRenderPlugin`（整个文件）。理由与核查见 MIGRATION §1；如果维护者希望保留该插件类（例如后续要挂别的 client hook），可改为「保留类但去掉 `beforeScriptsLoaded` 覆写」——这会留下一个空壳 @RegisterNekoJSPlugin，故本票选择删除并在 AC9 保持未勾选。
- **是否引入未裁定语义**：本票把「`ClientRenderRegistry.clearAll()` 是否保留 public」判为保留（诊断/测试用），未扩大其它公开面；未新增公开事件或 binding。

### 6.3 已知标准缺口（本轮未修，如实记录）

1. ~~`guardLint` 未跑~~ **已在交付前补跑**：BUILD SUCCESSFUL（守卫块 291、扫描 440 个文件、超限豁免 0、警告 0），见 §4 命令 10 与 `command-output/10-guardLint.txt`。
2. **未跑 1.21.1 与 fabric 节点**：共享树改动对 1.21.1 生效，未在 1.21.1 节点编译/测试（见 §5）。
3. **`hasHud` 对已销毁 Context 的条目仍返回 true**：既有共享树语义，本票不改（见 MIGRATION §2）。

## 7. 失败诊断（真实发生过的红）

1. **③ `aGenerationThatRegistersNothingRetiresThePreviousRenderers` 红**（`expected: <true> but was: <false>`）—— 生产缺陷，见 §2.1；修复方式为「空批次也注册候选计划 + 增加领域收集器」。
2. **② `aRejectedCandidateKeepsServingThePreviousActiveRenderer` 红于第 134 行** —— 测试缺陷：peer 收集器只增不减，每轮 reload 都再拒绝一次；改为一次性拒绝（`rejected[0]` 标志）。前 127-130 行的核心断言本已通过。
3. **④ `keyBindListenersFollowTheSameCandidateBoundaryWithoutDuplication` 红** —— 测试断言错误：候选期生产路由本来就服务**旧 generation**（旧代已注册同一 id `ticket26:key_a`），原断言写「空集」不成立；改为断言恰为旧代那一条。
4. **`ClientRenderRegistry.java:194` 编译失败**：剪枝 lambda `other -> other != batch` 捕获非 effectively-final 变量；改为 `Candidate current = batch;` 并去掉对参数的重赋值。
5. **`Ticket26ClientInputHudSurfaceTest.java:85` 编译失败**：`ClientEvents.HUD instanceof RenderRegistrationBusJS` 泛型不可转换（`EventBusJS<PainterJS,Void>`）；改用 `RenderRegistrationBusJS.class.isInstance(...)`。
6. **测试 harness 未注册新收集器**：`Ticket26ClientInputHudHarness` 装配缺 `ClientRenderDomainOwner`，③ 仍红；补上生产同款注册后转绿。

## 8. 归档与可追溯

- 迁移与删除条件见同目录 `MIGRATION.md`。
- 最小可运行示例：`examples/client-input-hud.js`。
- 原始命令日志：`command-output/01..09`。
- 测试源码：`src/test/java/com/tkisor/nekojs/client/render/Ticket26ClientInputHud{Harness,LifecycleTest,SurfaceTest}.java`。