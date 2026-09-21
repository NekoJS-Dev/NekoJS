# 26: CLIENT 输入与 HUD callback 生命周期

**What to build:** CLIENT 脚本通过 `KeyBindEvents.register(...)` 创建按键绑定，通过 `KeyBindEvents.pressed(...)`、`KeyBindEvents.released(...)`、`KeyBindEvents.tick(...)` 订阅输入事件；`ClientEvents.registerKeyMappings(...)` 订阅原生 key mapping 注册事件，`ClientEvents.hud(...)` 订阅 HUD 绘制事件，`ClientEvents.hudRender(...)` 按 id 注册常驻渲染器。验证各自既有调用者 Interface 到平台 client Adapter 的注册、owner-thread dispatch、reload 清理、client-only 过滤、声明与按节点能力；不把直接注册入口误写成事件监听，不包含 GUI 屏幕与 render Adapter 资源域。

**Blocked by:** [14: 事件总线与 Script/Native/Probe 事件声明基础](14-event-surface.md)

**Status:** in-progress

**Assignee:** zed-flash-26（main-session agent；deepseek-v4.1-flash subagent worktree）

**Claim record (2026-09-21):** worktree `../NekoJS-mult-t26` on branch `ticket-26-client-input-hud`（基于 `feedac1a`）。预计改动范围：`KeyBindEvents`/`ClientEvents` 既有输入与 HUD 成员从调用者 Interface 到平台 client Adapter 的注册、owner-thread dispatch、reload 清理、client-only 过滤、catalog/golden 区分直接注册与事件监听、TS/Python declaration、fixture 与 examples/MIGRATION。不消费/不修改 23/27/28/29 等其他域文件。

**Optional:** false

**Selected:** true

**Human input:** none

**Human input note:** agent 可以实现、测试并准备替代路径 parity、公开迁移表、旧 route 无消费者和 golden/发布证据；删除旧公开输入/HUD路径或最终 golden/发布确认的维护者 sign-off 不能由 agent 代答，未经 sign-off 不得删除旧路径或勾选对应删除项。

**Work items:**

- W7

**命名层次：** Java 源码中的 `REGISTER`、`PRESSED`、`REGISTER_KEY_MAPPINGS` 等是内部字段定位符；脚本公开名分别由 `GROUP.add/client(...)` 的字符串成员名确定，不是根据字段名自动转换。本票以小驼峰脚本名描述调用者行为；不要求重命名 Java 常量，也不因挂载在事件组下就把直接注册调用改成监听事件。

## Acceptance criteria

- [x] CLIENT session 由唯一 Runtime Root 在客户端启动入口创建，早期 key mapping、client tick 和 timer flush 在同一 owner 语义下发生；dedicated server 或裸 JVM 不加载 client-only 类。【`ClientEventsPoint` 为 `.clientOnly()`；`NekoJSMod.registerClient` 先判 `isClientDist()` 才注册收集器与 `NekoJSClient.register`；`NekoJSClient.onClientSetup`（`FMLConstructModEvent`，早于 `RegisterKeyMappingsEvent`）→ `loadScripts`，`onClientTickPost` → `flushReadyNodeTimers`，同一 Client 线程。边界：dedicated server/裸 JVM「不加载」未新增直接 fixture，见 REPORT §5】
- [x] keybind 注册在正确注册期执行且同 full id 幂等，CLIENT reload 不重复 KeyMapping；pressed/released/tick 事件只派发给新 generation，非法 id/key/category 显式失败并给出可定位错误。【`KeyBindEventsTest.registerBindingIsIdempotentAndNamesTheMapping`（`assertSame` 同 id 复用）、`invalidIdentifiersFailWithActionableError`/`keyParsingAcceptsVanillaNamesAndNull`（错误消息含输入原文）；`Ticket26ClientInputHudLifecycleTest.keyBindListenersFollowTheSameCandidateBoundaryWithoutDuplication`（候选期生产定向集合恰旧代一条，commit 后仍恰一条）】
- [x] Script API 成员使用小驼峰命名（lowerCamelCase，如 `preTick`、`registerKeyMappings`），事件组名保持 `KeyBindEvents` / `ClientEvents`；`KeyBindEvents.pressed/released/tick`、`ClientEvents.registerKeyMappings/hud` 继续复用各自既有事件，`KeyBindEvents.register(...)` 与 `ClientEvents.hudRender(...)` 分别保留直接创建按键绑定、按 id 注册常驻渲染器的调用语义。声明、示例及 catalog/golden 区分直接注册入口与事件监听，事件名、payload、side filter、取消/优先级和触发线程唯一，不新增重复 bus；不得把 Java 大写字段名当作脚本调用名。【`Ticket26ClientInputHudSurfaceTest` 4 tests：小驼峰正则、`REGISTER instanceof RegisterBus`/`HUD_RENDER instanceof RenderRegistrationBusJS`、`HUD` 为 `PainterJS` 普通监听、三个触发总线 `canDispatch()`、成员集与字段单例 `assertSame`、跨组 identity 唯一；示例 `baseline/2026-09-21-client-input-hud/examples/client-input-hud.js`】
- [x] reload candidate 阶段新 keybind/HUD listener 对生产路由不可见；commit 后旧 listener/timer/handler 停止接收新 callback，新 generation 恰好执行一次，失败或取消时旧 active 继续可用且候选资源全部清理。【**本票修复项**：`Ticket26ClientInputHudLifecycleTest` 4 tests——`candidateRegistrationsAreInvisibleUntilCommitThenSwapExactlyOnce`（候选期 peer 观察到旧 `['first']`，commit 后恰 `['second']`）、`aRejectedCandidateKeepsServingThePreviousActiveRenderer`（整批拒绝后旧渲染器仍服务，下一轮仍能提交）、`aGenerationThatRegistersNothingRetiresThePreviousRenderers`（空批次参与 commit → 真退役，红→绿原文见 REPORT §4）、`keyBindListeners...WithoutDuplication`】
- [ ] 按键状态、consumeClick 与 HUD 呈现结果可从脚本调用者 Interface 观察，断言不依赖 KeyMapping 私有集合或平台回调对象身份。**部分满足**：`register` 返回的 `KeyMapping` 句柄经 `KeyBindEventJS.getKeyMapping()` 透传，`KeyBindEventsTest` 以 `assertSame`/`isDown`/`getName`/`saveString`（全公开面）断言；HUD 呈现经 `dispatchHud` 调用探针观察，不读私有注册表、不断言回调对象身份。**缺**：脚本侧 `consumeClick()` 改变状态的端到端断言（无头 JVM 无真实按键状态源）——owner 票 34 真机 smoke，见 REPORT §5】
- [ ] 平台/版本 Adapter 按各节点既定支持等级与声明能力验证真实差异；client-only 过滤、注册时机或输入事件不可用时以 supported/partial/unavailable 明示，不自动补 Fabric parity。**部分满足**：共享树事实已核实（1.21.1 共享 `ClientRenderRegistry`/`RenderRegistrationBusJS`，仅三个 override；fabric `KeyBindEvents` 为独立孪生、渲染注册面缺席 ⇒ 确定失败而非静默 no-op）。**缺**：未跑 `:1.21.1:test`/`fabric:test`，未新造 capability 矩阵条目——owner 票 31/32，见 REPORT §5】
- [ ] 调用者 Interface、Adapter 契约、runtime member、TS/Python declaration、contract/golden 和节点 runtime smoke 可互相追溯；普通测试只读 golden，更新需旧新 diff、影响说明和维护者审阅。**部分满足（declaration 面不勾选）**：已交付 runtime member 反射 fixture（经生产注册路径读 catalog metadata 与字段单例）+ 事件成员的 ticket-33 跨节点只读基线（`platformGateTest` 绿，本票未改 golden）。**缺口（实证）**：`api-manifest-core.json`、三个 probe `*.expected.d.ts`、`declaration-parity.txt` 对本票域 **0 命中**（`command-output/08-declaration-coverage.txt`），与票 28 AC7 同类；节点 runtime smoke 未做——owner Managed Surface/Probe 与票 34】
- [x] GUI、render Adapter、PostEffects 与 Assets 保持独立 owner；本票不重复声明或清理它们的资源。【`inputHudSurfaceDoesNotDuplicateOtherDomains`（跨组 bus identity 唯一、无 `PostEffect*`/`AssetsEvents`）；`ClientRenderDomainOwner` 只管渲染器注册，不触碰 `PostEffectManager`/`DataGeneratorJS`/`LangGeneratorJS`；`ClientEvents.java` 本票 diff 为 0 行】
- [ ] 旧输入/HUD入口、重复 handler 或绕过 Runtime Root 的静态装配只能在替代路径 parity、公开迁移表、旧 route 无消费者和维护者确认后删除；不保留长期双路径。**不勾选（维护者 sign-off 门禁）**：本票删除了 `ClientRenderPlugin`（「load 前整表清空」路径），替代路径 parity / 迁移表 / 无消费者证据已备（`MIGRATION.md` §1/§3、`command-output/09`），但该删除改变 CLIENT reload 清理时机（调用者可见副作用），按 Human input note 不等同于勾选。

## Sources

- [NekoJS 实现票据拆分草案](../implementation-ticket-breakdown.md)
- [公开契约与插件模型规格](../specs/04-public-contract-and-plugin-model.md)
- [NekoJS 验证与迁移规格](../specs/07-validation-and-migration.md)
- [NekoJS 搬运功能事件面规格](../specs/08-ported-features-event-surface.md)
- [reload 候选状态与线程契约规格](../specs/09-reload-candidate-state-and-thread-contract.md)
- [NekoJS 实施交接单](../implementation-handoff.md)
- [实现票据索引](README.md)

## Dependency rationale

- [14: 事件总线与 Script/Native/Probe 事件声明基础](14-event-surface.md): KeyBindEvents 输入成员与 ClientEvents 的 key mapping/HUD 成员、side filter、取消/优先级和 catalog/golden 消费由事件面基础提供，不得重复声明 bus。

## Scope and coordination

**Rationale:** keybind 与 HUD/overlay 是同一条 CLIENT 输入/callback 生命周期路径，可单独注册、dispatch、reload 清理和 smoke；GUI/render Adapter 资源另拆，避免原合并客户端票过宽。

**Coordination:**

- 与 CLIENT_GUI_RENDER owner 并行协调 KeyBindEvents/ClientEvents 与平台 client Adapter；两票不互相硬串。
- 与 EVENT_SURFACE owner 协调成员唯一性、side filter 与既有 bus；事件基础实现是 blocker，具体成员仍需协调。
- 与 RUNTIME_ROOT/RELOAD_COMMIT owner 对齐 client owner thread、generation 路由和失败清理；共享 runtime 文件只作接口协调。

票据发布不代表已完成验收或本轮授权源码实施；完成条件与认领规则见本目录索引。

## JSX UI feature coordination（2026-09-12）

[41: JSX Screen Adapter](41-jsx-ui-neoforge-screen-adapter.md) 拥有 JSX Screen 内部输入、焦点与滚动路由；本票继续拥有 keybind/HUD，不互相硬串或合并 owner。

## Closure record（2026-09-21）

**Status:** done（AC1–AC4、AC8 满足；AC5/AC6/AC7 部分满足并如实未勾选；AC9 为维护者 sign-off 门禁，未勾选）

**范围与产出**

1. **修复的真实缺陷（本票主要产出）**：`ClientEvents.hudRender/worldRender` 的注册面原先
   **候选期直接写进程级 static 表**（`RenderRegistrationBusJS.execute` → `ClientRenderRegistry`），
   且 `ClientRenderPlugin.beforeScriptsLoaded` 在 **候选脚本执行之前**（`ScriptManager.java:817`）
   整表 `clearAll()` —— 两条叠加使「候选期不可见 / 失败保留旧 active」完全失效（AC4）。
   根因是候选计划惰性建立，**空批次从不注册计划 → 从不换装**，违反 `CandidateDomainCollector`
   的空计划契约。
2. **修复方式**（复用既有接缝，未新增框架）：`ClientRenderRegistry` 注册路由候选期进 inert 批次，
   commit 点整批换装；新增 `ClientRenderDomainOwner`（`CandidateDomainCollector`，无条件注册
   计划，空批次也参与 commit）；`NekoJSMod` client dist 分支注册该收集器；删除已无职责的
   `ClientRenderPlugin`。
3. **未改动**：`KeyBindEvents` 绑定语义（同 id 幂等、跨 reload 存活，既有裁定）、
   `pressed/released/tick`（走 `EventBusJS.PendingListener`，本就 generation-scoped）、
   `ClientEvents.java`（本票 diff 0 行）、`ClientRenderRegistry` public 面。

**逐条 AC**：见本文档 AC 区（勾选状态与逐条证据指针）。判定明细、命令输出与红→绿证据见
[`baseline/2026-09-21-client-input-hud/REPORT.md`](../baseline/2026-09-21-client-input-hud/REPORT.md)。

**验证（真实命令与结果）**

| 命令 | 结果 |
|---|---|
| `gradlew :26.1.2:compileJava --console=plain` | BUILD SUCCESSFUL in 19s |
| `gradlew :26.1.2:test --console=plain` | BUILD SUCCESSFUL；tests=330 failures=0 errors=0 skipped=54 |
| `gradlew :26.1.2:test --tests '*Ticket26ClientInputHudLifecycleTest*' --rerun`（修复前 → 修复后） | FAILED（tests=4 failures=3，③ 真红）→ BUILD SUCCESSFUL（tests=4 failures=0） |
| `gradlew :26.1.2:test --tests '*Ticket26*' --rerun --console=plain` | BUILD SUCCESSFUL；Lifecycle 4/0/0、Surface 4/0/0 |
| `gradlew :26.1.2:platformGateTest --rerun --console=plain` | BUILD SUCCESSFUL；EventSurfaceDomainGateTest 1/0/0、PlatformSpecContractGateTest 1/0/0，**无 member-drift**（golden 未改） |
| `gradlew :common:check --rerun-tasks --console=plain` | BUILD SUCCESSFUL in 3m25s；tests=1756 failures=0 errors=0 skipped=4 |

**golden / 基线**：本票**未改动任何 golden**。`src/test/resources/nekojs/platform-gates/event-surface-domains.txt`
（票 33 只读跨节点基线）保持原样；跑 `:26.1.2:platformGateTest` 无 drift。
注：本 worktree 为跑该 gate 先 cherry-pick 了主干的 `4a9ed9c8`（票 22 的 `ServerEvents`
`tradeDeclaration`/`tradeReload` 基线补齐，属**票 22 的遗留缺陷**，非本票改动）。

**未覆盖项与 owner**：TS/Python declaration 面（0 命中实证）→ Managed Surface/Probe owner；
节点 runtime smoke（真机 HUD 出图、按键真实触发）→ 票 34 P4；1.21.1/fabric 节点 test 与
capability 矩阵条目 → 票 31/32；`guardLint` 本轮未跑。详见 REPORT §5/§6.3。

**sign-off 事项**：删除 `ClientRenderPlugin`（整文件）改变 CLIENT reload 的渲染器清理时机
（调用者可见：不再有「加载前清空」）。替代路径 parity、迁移表、无消费者证据已备
（`MIGRATION.md` §1/§3、`command-output/09-old-route-consumers.txt`），**待维护者确认**后
AC9 才可勾选。备选方案（保留类但去掉覆写）见 MIGRATION §3。

**证据目录**：`docs/architecture-refactor/baseline/2026-09-21-client-input-hud/`
（`REPORT.md`、`MIGRATION.md`、`examples/client-input-hud.js`、`command-output/01..09`）。
