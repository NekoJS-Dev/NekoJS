# 27: CLIENT GUI 与 render Adapter 资源呈现清理

**What to build:** CLIENT 只读错误报告等 GUI 调用路径与 render/screen/world render Adapter 资源从注册、呈现到 generation 清理的完整路径；事件成员、side filter、client-only 过滤、声明、按节点能力与迁移可验证，不包含 keybind/HUD 输入域。按[专项产品决定](../editor-removal-and-error-ui.md)，内置 workspace GUI、菜单和编辑器不再属于本票可用性或验收要求。

**Blocked by:** [14: 事件总线与 Script/Native/Probe 事件声明基础](14-event-surface.md)、[30: 错误诊断、telemetry、workspace 与用户报告链路](30-diagnostics.md)

**Status:** in-review（实现/测试/证据已交付；AC1/AC6/AC7 部分满足未勾选，AC9 维护者门禁未勾选）

**Assignee:** zed-flash-27（main-session agent；GLM-5.3 subagent worktree）

**Claim record (2026-09-27):** worktree `../NekoJS-mult-t27` on branch `ticket-27-client-gui-render`（基于 mult HEAD）。预计改动范围：只读 error dashboard/错误报告 GUI 调用路径与 render/screen/world render Adapter 资源从注册、呈现到 generation 清理的完整路径 fixture、ClientEvents GUI/render 成员的 catalog/golden 只读核对、平台 client Adapter 挂载期与 render owner thread 分发断言、reload candidate 不可见/commit 后恰一次呈现/失败保留旧 active 的生命周期测试、票 30 已 closed 的 `DiagnosticOpenAction` 非 GUI seam 消费证据、按节点 capability（supported/partial/unavailable）矩阵、`baseline/2026-09-27-client-gui-render/` 证据。不修改：keybind/HUD/输入域（票 26 域）、Assets/Lang（29 域）、PostEffects（28 域）、JSX runtime（40–48 feature 票）；golden 只读，旧公开路径删除与发布性 golden 更新留维护者门禁（只备 parity/迁移/无消费者/旧新 diff 证据）。

**Optional:** false

**Selected:** true

**Human input:** none
**Human input note:** `none` 只表示实现、测试和证据整理可由 agent 执行；验收条件中涉及的维护者删除确认与 golden 更新审阅是后续发布门禁。agent 可以准备替代路径 parity、迁移表、旧 route 无消费者证据和旧新 golden diff，但不得在获得维护者 sign-off 前删除旧公开路径、更新发布性 golden 或勾选对应验收项，也不因此把本票改判为 `ready-for-human`。

**Work items:**

- W7

**Implementation record (2026-09-27, zed-flash-27):**

- 交付：①票 30 `DiagnosticOpenAction` 非 GUI seam 的 GUI 侧消费——`LocalErrorSource.resolve(
  DiagnosticOpenAction,…)` 与 `ErrorOpenService.openAsync(DiagnosticOpenAction,…)`，与 DTO
  （wire 投影）入口共用同一套位置校验/分派收尾，无第二事实源；②AC4 修复（红→绿）——候选
  失败/取消当刻释放渲染器注册批次：`CandidateStatePlan` 新增 `default discard()`（既有实现
  零改动），`GenerationGlobals.discard()/close()` 逐计划通知（失败记 WARN `[NEKO-1001]` 继续
  teardown），`ClientRenderRegistry.Candidate.discard()` 移除批次 + `registerCandidateBatch`
  先注册后入表 + 只读观察面 `pendingCandidateRegistrations()`；③GUI 快照不可变 fixture；
  ④render/screen 成员唯一性、平台类型边界、Dist.CLIENT 挂载期、golden 只读一致性 fixture
  （`Ticket27ClientGuiRenderSurfaceTest`）。`ClientEvents`/`ClientRenderEvents`/`DashboardView`
  /`NekoJSNetwork` 等 Adapter/呈现文件零行 diff；golden/probe 产物零改动。
- 红→绿（`baseline/2026-09-27-client-gui-render/command-output/01-red` vs `02-green`）：候选
  失败当刻批次释放两测先行红（`expected: <0> but was: <1>`），实现 discard 通知后绿。
- 验证（真跑，摘录见 command-output/）：`:26.1.2:test` 全量 448/0、`:26.2.0:test` 448/0、
  `:1.21.1:test` 313/0、`:common:check --rerun-tasks`（19/19 tasks，tests=1936/0，含隔离检查）、
  `guardLint`（320 守卫块/469 文件/0 警告）、`:26.1.2:platformGateTest`（无 member-drift）。
  本机 Windows 结果，不代表其他平台/CI/release。
- 遗留与 owner（REPORT §5）：AC1「打开日志」不在已批准 blue-a-2 原生界面元素清单内（与
  editor-removal 文 §3「保留打开日志」口径差，owner 维护者裁定）；结构化 open-action 不上
  wire（golden 冻结，wire 演进需维护者审阅）；declaration 面 0 命中（owner 09/33/34）；
  capability 矩阵条目（owner 31/32）；真机 GUI/render smoke（owner 票 34，not run）；
  fabric 节点 test 未跑（本票零 fabric 专属改动）。NEKO-1001 已在
  `wiki/en_us/Error-Reference.md` 同变更登记（该注册表在本分支存在）。

## Acceptance criteria

- [ ] error dashboard 等只读 GUI 调用者 Interface 能打开、渲染列表/详情/过滤/复制/日志与有效本地定位动作并报告错误；本票不要求 workspace、菜单或编辑器可用，也不接受编辑、保存、上传或下载脚本作为 GUI 验收。错误与诊断事实只来自票 30 的 frozen diagnostic record，GUI 不缓存可变共享状态、不建立第二事实源或旧 DTO 旁路；现状 `fullDetails` 只是编辑器删除工作流的过渡快照证据，不能替代该目标 record。**部分满足**：打开/列表/详情/过滤/选中/复制/复制全文/校验本地 VS Code 打开/错误反馈为既有只读面板（blue-a-2），经既有 `DashboardViewTest`/`DashboardLayoutTest`/`DashboardTextTest` 观察本票零 diff；本地定位动作已在 seam 层消费票 30 record——`Ticket27DiagnosticOpenActionConsumeTest`（`openAction()` 经 `payload()/parse()` 无损往返 → `ErrorOpenService` 分派到已验证本机文件；同一失败的 DTO 投影与 seam 解析逐字段相等＝无第二事实源；无定位/远端/虚拟路径显式拒绝）；GUI 不缓存可变共享状态——`Ticket27ErrorDashboardSnapshotTest`（不可变快照）。**缺**：①「打开日志」动作不在已批准 blue-a-2 原生界面元素清单内（editor-removal 文 §9 vs §3 口径差），现状未实现——owner 维护者（MIGRATION §3.2）；②结构化 open-action 不上 wire（wire 冻结，golden 只读）——见 REPORT §2 AC1】
- [x] render、world render 和 screen render callback 继续复用现有 ClientEvents/render 事件与 Adapter；事件名、payload、side filter、取消/优先级和触发线程在 catalog/golden 中唯一，不新增重复 bus。【`Ticket27ClientGuiRenderSurfaceTest.renderAndScreenMembersStayInTheSingleClientEventsSurface`（生产注册路径成员集、字段单例 `assertSame`、payload `PainterJS`/`ScreenRenderEventJS`、`scriptType()==CLIENT`、注册入口 vs 监听形态、跨组 bus identity 唯一）+ `catalogGoldenKeepsRenderMembersUniqueAndFabricExplicitlyWithoutThem`（票 33 golden 每节点成员恰一次）；`ClientEvents.java`/`ClientRenderEvents`/`RenderRegistrationBusJS`/`ClientRenderRegistry`（除 AC4 修复外）零 diff——复用即现状；触发线程由 `ClientReloadExecutor`（CLIENT owner thread = Render 线程）与 `ClientRenderRegistry` javadoc 承载】
- [x] 平台 client Adapter 在正确注册期挂载 render/screen 资源并在 render owner thread 分发；Adapter 持有 MC/loader 类型，shared 作者契约不引入平台类型。【`Ticket27ClientGuiRenderSurfaceTest.platformRenderAdapterMountsOnTheClientDistOnly`（`ClientRenderEvents` `@EventBusSubscriber(value=[Dist.CLIENT])` 反射断言——mod 构造期挂载、dedicated server 不订阅）+ `sharedRenderRegistrationSurfaceCarriesNoPlatformTypes`（`ClientRenderRegistry`/`RenderRegistrationBusJS` 签名与字段 0 个 `net.minecraft`/`net.neoforged` 类型，渲染上下文以 Object 透传；MC 面包装 `HudRenderContextJS`/`WorldRenderContextJS`/`ScreenRenderEventJS`/`PainterJS` 0 个 loader 类型）】
- [x] reload candidate 阶段新 GUI/render 资源与 listener 对生产路由不可见；commit 后旧 generation 停止接收 callback 并按所有权释放，新 generation 恰好呈现一次，失败或取消时旧 active 继续可用且候选资源全部清理。【本票修复项，红→绿见 `baseline/2026-09-27-client-gui-render/command-output/01-red`/`02-green`：`Ticket27ClientGuiRenderLifecycleTest` 4 tests——`aRejectedCandidateReleasesItsPendingRendererBatchAtFailureTime`/`aCandidateKilledDuringExecutionReleasesItsPendingBatchAndKeepsTheOldActive`（修复前红 `expected: <0> but was: <1>`＝失败当刻批次滞留；修复后失败当刻释放、旧 active 继续服务、下一轮正常提交）、`worldRenderPresentationsSwapExactlyOnceAtTheCommitPoint`（候选期生产路由观察、commit 恰一次、空批退役）、`screenRenderAndHudListenersFollowTheSameCandidateBoundary`（监听面候选 pending/commit 接管/空代退役）；渲染器恰一次呈现由 `List` 探针断言，`pendingCandidateRegistrations()` 为新增只读计数观察面；主会话复核追加：单计划 discard 抛错不阻断其余计划清理由 `GenerationGlobalsDiscardResilienceTest` 钉住（NEKO-1001 记录后继续拆除），close 时对已发布计划的 discard 幂等无二次释放同样有断言】
- [x] GUI 操作、render context、取消和错误呈现可从调用者 Interface 观察；断言不依赖私有屏幕字段、Renderer 对象身份或未公开平台集合。【GUI：`DashboardView.Canvas` 捕获/`ErrorDashboardModel` 快照/`ErrorOpenService.Result`/`LocalErrorSource.Result` 公开结果对象；render：`dispatchHud/dispatchWorld` List 探针（本帧真正被调用的 id）、`hasHud/hasWorld`、`hasListeners()`、`pendingCandidateRegistrations()` 只读计数（新增发布面，刻意为计数而非集合暴露）；全 fixture 无私有字段反射、无回调对象身份断言】
- [ ] 平台/版本 Adapter 按各节点既定支持等级与声明能力验证真实差异；不可用或部分可用时以 supported/partial/unavailable 明示，不自动补 Fabric parity，也不把 experimental 当 primary。**部分满足**：golden 只读断言核实真实差异——NeoForge 三节点 ClientEvents 含全部 GUI/render 成员，fabric 两节点仅 `tick,tickPost,tickPre`（GUI/render 显式缺席＝unavailable，fixture 断言，不做静默 parity）；`ClientRenderRegistry`/`RenderRegistrationBusJS` 为 1.21.1+26.x 共享树文件（1.21.1 仅 `ClientRenderEvents`/两个 context wrapper 孪生）。**缺**：未新造 capability 矩阵条目（owner 票 31/32）；`:26.1.2-fabric:test`/`:26.2.0-fabric:test` 未跑（本票零 fabric 专属文件改动），见 REPORT §5】
- [ ] 调用者 Interface、Adapter 契约、runtime member、TS/Python declaration、contract/golden 和节点 runtime smoke 可互相追溯；普通测试只读 golden，更新需旧新 diff、影响说明和维护者审阅。**部分满足（declaration 面不勾选）**：runtime member 反射 fixture（经生产注册路径读 catalog metadata 与字段单例）+ golden 只读断言 + `:26.1.2:platformGateTest` 绿（无 member-drift，本票零 golden 改动）。**缺口（实证）**：`api-manifest-core.json`、probe `*.expected.d.ts`、`declaration-parity.txt` 对本票域 0 命中（`command-output/10-declaration-coverage.txt`），与票 26/28 AC7 同类——owner 09/33/34；节点 runtime smoke（真机 GUI/渲染出图）not run——owner 票 34】
- [x] PostEffects、Assets、recipe/loot/tags/JEI/capability/goal/keybind/HUD 等既有 owner 不被并入本票；本票只验证不重复声明这些事件或 binding。【本票 diff 不含上述域文件（`KeyBindEvents`/`PostEffect*`/`DataGeneratorJS`/`LangGeneratorJS` 等零改动）；`Ticket27ClientGuiRenderSurfaceTest` 跨组 bus identity 唯一 + golden 断言 render 成员不在其他组出现；`ClientRenderDomainOwner`（票 26 交付）本票未改其文件】
- [ ] 旧 render/目标 GUI 入口、重复 handler、不受测 wrapper 或绕过 Runtime Root 的资源装配只能在替代路径 parity、公开迁移表、旧 route 无消费者和维护者确认后删除；不保留长期双路径。内置 workspace/编辑器 GUI 与编辑文件同步不因“旧公开路径”理由保留。**不勾选（维护者 sign-off 门禁）**：本票零删除；已备证据（`command-output/05-old-route-remnant-scan.txt`）：渲染分发 handler 唯一（生产调用点仅平台 Adapter 两节点孪生）、GUI 入口唯一（packet handler 单点）、内置编辑器链路 11 个符号生产源码 0 命中、`ClientRenderRegistry.clearAll()` 生产调用者 0；残留项（旧 UI lang key 4 个、日志入口、wire 演进）与删除条件见 `baseline/2026-09-27-client-gui-render/MIGRATION.md` §3/§4，待维护者裁定】

## Sources

- [NekoJS 实现票据拆分草案](../implementation-ticket-breakdown.md)
- [公开契约与插件模型规格](../specs/04-public-contract-and-plugin-model.md)
- [NekoJS 验证与迁移规格](../specs/07-validation-and-migration.md)
- [NekoJS 搬运功能事件面规格](../specs/08-ported-features-event-surface.md)
- [reload 候选状态与线程契约规格](../specs/09-reload-candidate-state-and-thread-contract.md)
- [NekoJS 实施交接单](../implementation-handoff.md)
- [内置游戏内编辑器移除与只读报错 UI 规划覆盖](../editor-removal-and-error-ui.md)
- [实现票据索引](README.md)

## Dependency rationale

- [14: 事件总线与 Script/Native/Probe 事件声明基础](14-event-surface.md): ClientEvents GUI/render 相关成员、payload、side filter、取消/优先级和 catalog/golden 消费由事件面基础提供，不得重复声明 bus。
- [30: 错误诊断、telemetry、workspace 与用户报告链路](30-diagnostics.md): 只读 error dashboard 必须消费统一诊断票冻结的 record、字段和 generation 语义；在旧错误结构上先行实现目标 GUI 会造成返工和第二事实源。编辑器删除本身不等同于该 record 已实现，也不被本票的前置关系阻塞。

## Scope and coordination

**Rationale:** GUI 与 render Adapter 共享呈现资源、owner thread 和清理边界，能形成一条新上下文可验的呈现路径；输入/HUD callback 生命周期另有触发与注册语义，继续拆开。

**Coordination:**

- 与 CLIENT_INPUT_HUD owner 并行协调 ClientEvents 与平台 client Adapter；两票不互相硬串。
- 与 EVENT_SURFACE owner 协调成员唯一性、side filter 与既有 render bus；事件基础实现是 blocker，具体成员仍需协调。
- 与网络/诊断 owner 协调 error dashboard packet 传输；票 30 拥有 diagnostic record，GUI 只做投影，不反向拥有错误事实源。Fabric 维持现有错误文本降级，本票不顺带承诺 Fabric 新面板。
- 与 POST_EFFECTS/ASSETS owner 并行协调 ClientEvents 使用点，避免同文件改动被误写成串行依赖。

票据发布不代表已完成验收或本轮授权源码实施；完成条件与认领规则见本目录索引。

## JSX UI feature coordination（2026-09-12）

新增 JSX runtime 与 Screen 能力归 40–48 feature 票；[41: JSX Screen Adapter](41-jsx-ui-neoforge-screen-adapter.md) 与本票协调客户端 Adapter。既有只读 error GUI/render callback 清理范围不变，不要求将 dashboard 改写为 JSX，也不反向依赖新 feature。
