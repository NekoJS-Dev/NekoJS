# 在审/进行中票据维护者批量裁决摘要（2026-09-29）

范围：票 20（runtime-commands）、26（client-input-hud，in-progress）、29（assets）、41/42/43/44（JSX UI）。
来源：各票面（`docs/architecture-refactor/implementation-tickets/NN-*.md`）及其 baseline/evidence 目录；本摘要只汇编票据已记录的结论，不重新推导。基线：主树 `mult` @`296330a0`。

关键外部事实（影响多票，先列出）：

- **2026-09-29 TS golden（票 23/24/27 声明缺口修复）已合入主树**：`src/test/resources/golden/events-declared/<node>.<side>-events.d.ts`（15 文件，提交 `7985420d3`），由 `DeclaredEventSurfaceGoldenTest` 在 `platformGateTest` 门禁内守护，证据 `evidence/2026-09-29-golden-decl-prep/REPORT.md`。维护者同日两项裁决（票 23/27 关闭记录，提交 `296330a0`）：**声明腿以 TS golden 为准**（Python .pyi 后补记 09/34）；**零删除 + 证据已备的删除门禁 AC 视为满足，删除动作留维护者**。
- **2026-09-28 真机会话**（`evidence/2026-09-28-realmachine-session/README.md`）只覆盖票 24（方块破坏取消）、27（错误面板/VS Code 定位）、30（record 存活）；**未触碰 keybind/HUD/consumeClick/JSX UI**。
- **票 31/32（fabric 面）与 09/33（managed surface / 声明面）均已 closed**——票 26/29 多个未勾项记录的 owner 已关闭，见各节"owner 悬空"标注。
- JSX UI 依赖链：40/45/46/47 已 closed；**48（end-to-end proof，ready-for-agent）仅剩 blocker 为 41/42/43/44**；42/44 认领时记录了对 in-review 票（29/30/40/41）的 blocker 偏差（维护者直接指令 + 产物已在分支可用）。

---

## 票 20：/nekojs 管理命令权限、生命周期入口与阶段诊断（in-review）

票据：`implementation-tickets/20-runtime-commands.md`。主树无对应 `baseline/` 目录；验证主张仅记录在票面 Delivery record（2026-09-23），原始输出在 worktree `../NekoJS-mult-t20`（分支 `ticket-20-runtime-commands`）。代码与测试已合入主树（`common/src/test/java/com/tkisor/nekojs/core/lifecycle/RuntimeCommandResultFormatterTest.java`、`src/test/java/com/tkisor/nekojs/command/RuntimeCommandLifecycleSourceTraceTest.java`；提交 `105487980`/`714751f43`/`ef61908e2`）。

**交付一句话**：`/nekojs` reload/test/error/packs/trust 改为经 `NekoRuntimeRoot` 生命周期结果对象执行（含 STARTUP reload 双 loader 拒绝、提交后 recipe/pack 错误按已提交 generation 单独报告），无 JSX/UI 扩张。

**AC 台账：1 勾 / 7 未勾**（共 8 项）：

| 未勾 AC | 记录的 gap（关键短语） | owner |
|---|---|---|
| AC1 权限 | "`.requires` hides the command without an explicit denial message; live unauthorized dispatch/no-reload behavior remains unverified" | 未指派（真机/命令 smoke，34 域） |
| AC2 唯一 root 入口 | "no live owner-thread command dispatch smoke" | 未指派 |
| AC3 成功/失败输出 | "no live command dispatch smoke" | 未指派 |
| AC4 watchdog 隔离后 reload | "live command recovery was not run" | 未指派 |
| AC5 边界稳定错误 | "live wrong-distribution command was not run" | 未指派 |
| AC6 错误命令展示 | "packet/error UI runtime and NetworkRegistrationSourceTraceTest were not run" | 未指派 |
| AC8 删除旧助手 | "live command route and deletion parity were not observed" | 未指派 |

（唯一勾选项 AC7 packs/trust：引票 19 closed 的 fixture，本 diff 未改 packs/trust。）

**验证主张（绿）**：`RuntimeCommandResultFormatterTest` + `NekoRuntimeRootReloadResultTest`（7 tests）；五节点 `RuntimeCommandLifecycleSourceTraceTest` + 两节点 `verifyFabricRuntimeArtifact`；`:common:check` 含 guardLint。

**关闭即主张**：8 项 AC 仅 1 项满足即关闭，等于主张全部 live 命令面（权限拒绝、owner 线程派发、恢复、wrong-distribution、错误面运行时）无需真机验证可接受——尤其 AC1 的"明确拒绝"与现状 `.requires` 隐藏式拒绝**直接相悖**，该项不是纯证据缺口而是行为缺口。

**不一致/标记**：主 checkout 无独立 baseline REPORT 或 command-output 归档（与 26/29 的证据形态不对称）；票面如实记录了一次无关 fixture 失败（Permission 静态初始化）未处理。

---

## 票 26：CLIENT 输入与 HUD callback 生命周期（in-progress，代码已全量合入主树）

票据：`implementation-tickets/26-client-input-hud.md`；基线 `baseline/2026-09-21-client-input-hud/`（REPORT.md、MIGRATION.md、followup-2026-09-25-ticket26.md、examples、command-output/01..17）。合入验证：`src/main/java/com/tkisor/nekojs/client/render/ClientRenderDomainOwner.java` 在、`ClientRenderPlugin.java` 已删（维护者 2026-09-25 sign-off 批准删除）、三个 Ticket26 测试在主树。

**交付一句话**：修复 `ClientEvents.hudRender/worldRender` 候选期直写进程级 static 表 + 加载前整表 `clearAll()` 两条叠加缺陷（候选 inert 批次 + `ClientRenderDomainOwner` 无条件注册空计划参与 commit），删除 `ClientRenderPlugin`，并补齐 KeyBindEvents/ClientEvents surface 与生命周期 fixture。

**AC 台账：6 勾 / 3 未勾**（共 9 项；AC1–AC4、AC8、AC9——AC9 经 2026-09-25 维护者 sign-off 勾选）：

| 未勾 AC | 记录的 gap（关键短语） | owner（票面记录 → 现状） |
|---|---|---|
| AC5 按键状态可观察 | "脚本侧 `consumeClick()` 改变状态的端到端断言（无头 JVM 无真实按键状态源）" | 票 34（ready-for-agent，未做） |
| AC6 节点/Adapter 能力 | "未跑 `:1.21.1:test`/`fabric:test`，未新造 capability 矩阵条目" | 票 31/32 → **两票已 closed，owner 悬空**（27 先例：按票自建 `CAPABILITY-MATRIX.md`，见 `baseline/2026-09-27-client-gui-render/CAPABILITY-MATRIX.md`） |
| AC7 声明/追溯 | "`api-manifest-core.json`、三个 probe `*.expected.d.ts`、`declaration-parity.txt` 对本票域 **0 命中**"（`command-output/08-declaration-coverage.txt`）；节点 runtime smoke 未做 | Managed Surface/Probe（09/33，均已 closed）与票 34 → owner 悬空/34 |

**未勾项此后被解锁的程度（重点核查）**：

- **AC7 声明腿：半边解锁。** 2026-09-29 TS goldens 含 `ClientEvents.registerKeyMappings`/`hud`/`hudRender`（三个 NeoForge 节点 `src/test/resources/golden/events-declared/{1.21.1,26.1.2,26.2.0}.client-events.d.ts`，实测 grep 命中；fabric 缺席与票 33 基线一致）。但 **`KeyBindEvents`（register/pressed/released/tick）被明确排除在该批 golden 范围外**（`evidence/2026-09-29-golden-decl-prep/REPORT.md` §4 列名排除），全部 5 个 client-events golden 0 命中；KeyBindEvents 目前仅由票 33 文本基线覆盖（`src/test/resources/nekojs/platform-gates/event-surface-domains.txt` L54–58）。按 23/27 的 TS-first 裁决口径，ClientEvents 侧可视为已覆盖，KeyBindEvents 侧需一次同款 golden 范围扩展。
- **AC9/删除门禁：不适用零删除先例**——本票是真实删除（`ClientRenderPlugin`），且已由 2026-09-25 维护者 sign-off 单独批准并勾选，无遗留。
- **AC5 真机：未解锁**——2026-09-28 真机会话范围仅 24/27/30，无 keybind/HUD。

**验证主张（绿）**：`:26.1.2:test` 全量（330/0/54）；Ticket26 Lifecycle 4/0 + Surface 4/0（含 AC4 红→绿）；`:26.1.2:platformGateTest` 无 member-drift；`:common:check`（1756/0/4）；guardLint；follow-up `KeyBindEventsTest` 10/0。

**关闭即主张**：9 项 AC 中 6 项满足；余窗口归 owner 34（AC5 consumeClick 真机、节点 runtime smoke）或改派 owner（AC6 节点 test/capability——31/32 已关需重新指派；AC7 KeyBindEvents 声明腿——扩展 events-declared golden）。

**不一致（票面注记 vs 基线 REPORT）**：

1. **AC4 票面已勾，REPORT 判"部分满足"**：REPORT §3 AC4 写"末半「候选资源全部清理」未在失败当刻兑现"，§2.1 如实限定"失败批次的 `Candidate` 对象不在失败当刻从 `CANDIDATE_BATCHES` 移除……没有直接断言"。票面 [x] 注记只引红→绿证据，未携带此限定。
2. **Closure record（2026-09-21）首行写 `Status: done`**，与票头 `in-progress` 矛盾；后续 follow-up（2026-09-25）与 Maintainer sign-off 已把口径修正为维持 in-progress。
3. AC2 的 REPORT 如实限定（`registeredKeys()` 断言不能检测同 id 重复注册、`pressed/released/tick` 无 `post()` 派发断言）未体现在票面 [x] 注记中。

---

## 票 29：Assets/Lang 资源生成与回读收口（in-review）

票据：`implementation-tickets/29-assets.md`；基线 `baseline/2026-09-21-assets-lang/`（REPORT.md、MIGRATION.md、examples、command-output/01..07，含 06-red/07-green）。

**交付一句话**：新增 plugin-only 最小语言声明面 `NekoJSPlugin.generatedLangs()`（默认 `en_us`）+ `PluginGenerationHooks.resolveGeneratedLangs`（TreeSet 归一、整批拒绝、单插件隔离），并**恢复被 `c8066519`/`59919f87` 误删的 `Assets` 绑定注册行**（真实回归修复，红→绿钉住）。

**AC 台账：6 勾 / 6 未勾**（共 12 项；勾选 AC1/2/4/5/10/12）：

| 未勾 AC | 记录的 gap（关键短语） | owner（票面记录 → 现状） |
|---|---|---|
| AC3 contract/golden 或回读 fixture | "本票未为本域新建 golden，PNG/JSON 输出的 golden 化未做，回读仍只由既有 `DataGeneratorJS#getJson` 测试覆盖" | 本票后续轮 / 票 33（已 closed → 悬空） |
| AC6 reload 恰一次 + 回读 | "「恰好触发一次」与「被实际 resource manager 回读」需真实客户端资源 reload……本票无真机/GameTest smoke" | CLIENT_GUI_RENDER owner（27，已 closed）/ 票 34 |
| AC7 client-only 过滤 | "dedicated server 进程级 smoke 断言……未验证" | loader/dist 面 / 票 34 |
| AC8 capability/fabric | "未新造 capability 矩阵记录，未对 fabric 跑 Assets/Lang 的 source trace 或 smoke"（另：`Assets` 带 `//? if >=26` 守卫，1.21.1 无该绑定，未正式记录） | 票 31/32（已 closed → 悬空） |
| AC9 TS/Python declaration | "未为 `Assets`/`generatedLangs()` 新增 declaration 条目……0 命中" | 票 09/33（已 closed → 悬空） |
| AC11 删除门禁 | "**不勾选（维护者 sign-off 门禁）**：本票未删除任何旧公开路径……「无适用对象」的判断仍由维护者确认" | 维护者 |

**未勾项此后被解锁的程度**：

- **AC9 事件腿：已解锁**——`ClientEvents.generateAssets`/`lang` 现在在三个 NeoForge 节点的 events-declared TS goldens 中（`26.1.2.client-events.d.ts` L449–452 等，实测命中）。按 TS-first 裁决可视为满足；但 `Assets` typed binding 与 `generatedLangs()` 属 binding/plugin 面，不在 events-declared golden 家族内，仍无声明覆盖。
- **AC11：与零删除先例同形**——票 23/27 的 2026-09-29 裁决（"零删除+证据已备的删除门禁 AC 视为满足"）可直接套用，只差维护者对"无适用对象"判断与 `Assets` 恢复归属的两项确认（票面"维护者 sign-off 项"节已列）。
- AC6/AC7/AC8：真机/dist/capability 腿未被任何后续会话覆盖。

**验证主张（绿）**：`:common:check`（1763/0/4）；`Ticket29AssetBindingTest` 2/0（红→绿）；`:1.21.1:test` 237/0；三节点 `platformGateTest` 无 member-drift；guardLint（288 块 0 警告）；`:26.1.2:test` 332/0。

**关闭即主张**：12 项 AC 中 6 项满足；AC11 按零删除先例、AC9 事件腿按 TS-first 先例可由维护者当场裁决勾选；AC3（本域 golden/回读 fixture）与 AC6/7/8（真机 reload、dedicated-server smoke、capability 矩阵）归后续轮/34/改派 owner。

**不一致（票面注记 vs 基线 REPORT）**：REPORT §6.2 自查汇总写"**4 条勾选、3 条部分满足不勾选、1 条 sign-off 门禁不勾选**"（合计 8），与其自身 §3 逐条表及票面（6 勾 / 5 部分 / 1 门禁，共 12）**不符**——§3 表与票面一致，§6.2 汇总漏计。

---

## 票 41：NeoForge 26.2 JSX Screen/输入/焦点/滚动 Adapter（in-review）

票据：`implementation-tickets/41-jsx-ui-neoforge-screen-adapter.md`。无 baseline 目录；证据在票面（"Implementation evidence and remaining gaps"三段：2026-09-25 live attempt、follow-up、close-path 根因修复）+ `docs/research/minecraft-mcp-dsh.md` + 主树 `src/main/java/com/tkisor/nekojs/client/ui/{JsxScreen,JsxHostAdapter,JsxHostTree}.java`、`src/test/resources/nekojs/client/ui/ticket41-screen-flow.tsx`、`Ticket41JsxHostAdapterTest`。

**交付一句话**：NeoForge 26.2 JSX Screen/host Adapter 与纯保留事务树已落地合入；无头 transaction/cleanup 与"paint 只走已提交 host tree"source-trace 绿；两轮真机排障修复了 startup `Minecraft.getInstance()` 空指针、viewport Java Map 归一与 teardown 先通知 guest 再入 CLOSING 的 NEKO-7001/7007 根因，截图证明真实 Screen 首绘（`screenshots/ticket41-fixed-startup.png`，见票面）。

**AC 台账：0 勾 / 8 未勾**——票面明言"all checkboxes below are intentionally unchecked"。总缺口（票面原句）："live first open/paint/input/resize/close/replacement, input cursor/selection/editing, pointer capture/pressed/hover/narration, complete scroll clipping/consumption, owner-thread guest runtime, actual deleted-event closure behavior, and real NeoForge MCP evidence remain gaps. No human acceptance is recorded." 具体：真实点击派发未证明（`[ticket41] clicked` marker 未记录——staged fixture 不在 loaded CLIENT 脚本中）、resize/close/替换/焦点/滚动未测。AC8 硬性要求注册 Minecraft MCP smoke，其它节点只记 `not verified`。

**验证主张（绿）**：`Ticket41JsxHostAdapterTest`（含 `screenTeardownNotifiesTheGuestBeforeEnteringClosing`）；`:26.2.0:test`（26 tests）+ `:26.2.0:build`；`:common:check`；`:26.2.0:compileJava` / `:26.2.0-fabric:compileJava`（强制重编后）。

**关闭即主张**：0/8 未满足即关闭等于主张无真机交互证据的 Screen Adapter 可交付——与 AC8 的 MCP smoke 硬要求直接冲突，**不可关**；且 41 是 42/44 的 blocker 偏差来源与 48 的前置。

**不一致/标记**：票面如实区分"截图可见按钮 hover"与"真实点击派发"，无发现票面与证据矛盾；唯主树无独立 baseline 归档（真机过程细节只在 research 文档与票面）。

---

## 票 42：JSX UI CLIENT generation/reload/诊断/cleanup 接线（in-review）

票据：`implementation-tickets/42-jsx-ui-generation-reload-cleanup.md`。无 baseline 目录；证据全在票面 Delivery record（2026-09-25）+ 双轴 code-review 记录。实现主体随 `1136f30a`（42/44/45/47 批次）先落地，本轮为逐条 AC 验证、review 补缺（`requireUsable` 线程检查扩面至全部状态变更/guest 入口、诊断码区段表登记）与记账。

**交付一句话**：UI root 绑定唯一 `NekoRuntimeRoot` 的 CLIENT generation（`UiRootLifecycle`、`GenerationGlobals.uiRoots` 实例字段、`ScriptManager.registerUiRoot` seam），候选不可见/失败保留 active/取代释放/幂等 teardown/非 owner 线程 NEKO-7004 显式失败/UI 错误进票 30 统一诊断链路，全 `client/ui` 包无可变 static。

**AC 台账：6 勾 / 1 未勾**（共 7 项）：

| 未勾 AC | 记录的 gap（关键短语） | owner |
|---|---|---|
| AC7 真实 26.2 smoke | "未运行：Minecraft MCP 已接入且可截图，但 26.2 GUI/命令自动控制不可用……成功/失败 reload 及 active UI 保留均未完成真机验收"；`Ticket42UiGenerationLifecycleTest` 已把五类场景定为 Minecraft-free 可执行契约 | 注册 smoke 环境（与 41 真机轮同源） |

**验证主张（绿）**：`:26.2.0:test`/`:26.1.2:test` UI 焦点套件（Ticket42 两节点各 9/0，含 review 修复后复跑）；全套 `:common:test :26.1.2:test :26.2.0:test` + `:1.21.1:compileJava` + 两 fabric 编译 BUILD SUCCESSFUL（common 1928/0/4skip，26.x 各 437–438/0/58skip）。

**关闭即主张**：7 项 AC 中 6 项满足；AC7 真机腿与 41 的真机缺口同源同轮（成功/失败 reload、旧 Screen 关闭、旧事件失效、active UI 保留），关闭即主张该腿由后续注册 smoke 轮交付。

**不一致/标记**：无票面-证据矛盾；需维护者知悉的偏差已在 Claim record 自记（30/40/41 in-review 时认领 + `1136f30a` 批次单提交合并四票的实现先落地后补记账）。

---

## 票 43：六档 Viewport Profile 与响应式布局（in-review）

票据：`implementation-tickets/43-jsx-ui-viewport-profiles.md`。无 baseline 目录；确定性 fake 证据为 `common/src/test/resources/nekojs/language-ts-examples/tsx/ui-core.tsx`，公开契约源于 `common/src/main/resources/nekojs/node/modules/jsx-runtime.ts`（声明自动抽取）。

**交付一句话**：common 侧 `UI.profileFor` 六档判定（safe-area 后内容宽高档位 320/480/640/854/1280 与 180/240/360/480/720，tie-break 取低档、`maxProfile` 封顶、GUI scale 仅作输入）、profile 覆盖解析（exact → lower → base）、逻辑/设计坐标共存、resize 只失效 measure/arrange 不重跑 render，并附三项 common runtime 加固（跨 Fragment 重复 key 拒绝、commit 失败重试安全 close、owner-thread-first signal updater）。

**AC 台账：4 勾 / 2 未勾**（共 6 项）：

| 未勾 AC | 记录的 gap（关键短语） | owner |
|---|---|---|
| AC2 golden 覆盖 | "no golden file was regenerated or maintainer-reviewed in this ticket"（确定性 fake fixture 已覆盖解析顺序/非法值/诊断） | 维护者审阅（按 `baseline/2026-09-12-managed-surface/REGENERATE.md` 流程再生成 + 旧新 diff） |
| AC6 真实 resize smoke | "not run: real NeoForge 26.2 resize smoke is owned by ticket 41/client evidence" | 票 41 真机轮 |

**验证主张（绿）**：`:common:test --tests NekoTypeScriptJsxRuntimeTest --tests NodeModuleTypeDocsTest`；`:common:check` + guardLint；`git diff --check`；`npm run test:probe-types`（TS 5.8.3，exit 0，其 worktree 基线 `aa30e82f` 上）。

**关闭即主张**：6 项 AC 中 4 项满足；AC2 需一次受审的 golden 再生成后勾选；AC6 并入 41 真机轮。

**不一致/标记**：43 声称的 `npm run test:probe-types` 绿是在其自有 worktree 取得；**当前主树 `mult` 上该套件有预存失败**（14×TS7026，`jsx-primitive-props.tsx` 缺 `JSX.IntrinsicElements`，见 `evidence/2026-09-29-golden-decl-prep/REPORT.md` §6，owner 待定 JSX 链/09）——非 43 引入，但"验证至今仍绿"不成立。

---

## 票 44：文本测量、视觉样式、图片与受控资源解析（in-review）

票据：`implementation-tickets/44-jsx-ui-text-visual-assets.md`。无 baseline 目录；证据全在票面 Delivery record（2026-09-25，workbuddy-kimi-44）+ 双轴 review 记录（修复 opacity 组合语义、Resolver Javadoc 失实、诊断构造重复 3 项）。

**交付一句话**：`api/ui/FontAdapter` 为 common 唯一测量入口（`TextLayouter` 只消费 adapter 值，`McFontAdapter` 包 Minecraft `Font`）、`VisualStyleResolver` 12 个受控 prop 白名单（`UiColor`/`UiResourceId` 受控语法，无 CSS 字符串/URL/句柄入口）、`DiskPackUiResourceResolver` 复用票 29 同一物理根 `<gameDir>/nekojs`。

**AC 台账：4 勾 / 2 未勾**（共 6 项）：

| 未勾 AC | 记录的 gap（关键短语） | owner |
|---|---|---|
| AC4 加载/解码失败诊断 | "加载失败（NEKO-6005）与解码失败（NEKO-6007）按 Error-Reference 标注为 reserved 未发射——纹理加载/解码管线随 image blit 一并留待 version owner 接线" | version owner（后续实现轮） |
| AC6 真实 Screen smoke | "本会话无 Minecraft MCP/真实客户端环境……另 image 绘制当前为占位框（纹理 blit 未接线）" | 41 真机轮 + blit 管线 |

**验证主张（绿）**：焦点套件 common `api.ui` 46/0、26.2.0/26.1.2 UI 25/0 + painter 3/0、1.21.1 parity 3/0；全套（修复后无过滤器）common 1928/0/4skip、26.1.2 与 26.2.0 各 438/0/58skip、1.21.1 313/0/14skip、两 fabric 编译通过。

**关闭即主张**：6 项 AC 中 4 项满足；AC4/AC6 与"纹理 blit 接线 + 真机 smoke"绑定——这是**未实现的管线工作**而非待补证据，关闭即主张占位框图片渲染可交付。

**不一致/标记**：无票面-证据矛盾；Claim record 自记 blocker 偏差（29/40/41 in-review 时认领）。

---

## 批量裁决建议

| 票 | 建议动作 | 要点 |
|---|---|---|
| 20 | **需补小证据 + 需维护者裁决项** | 7 项未勾全卡 live 命令 smoke（建议归入 34 式真机轮一并跑）；AC1 的 `.requires` 隐藏式拒绝与票面"明确拒绝"相悖，需裁决"补显式拒绝"或"改权限口径"；主树无 baseline 归档，建议补一份 command-output 证据目录 |
| 26 | **需补小证据 + 需维护者裁决项** | AC5 consumeClick 真机归 34；AC7 KeyBindEvents 声明腿建议按 23/24/27 同款扩展 events-declared golden（`KeyBindEvents` 当前 0 命中）；AC6 owner 31/32 已关需改派（或按 27 先例自建 capability 表）；或按 23/27 部分满足先例裁决关闭并移交缺口 |
| 29 | **需维护者裁决项（可当场勾 2 项）+ 需补小证据** | AC11 套用 2026-09-29 零删除先例可直接裁决（附"无适用对象"+ `Assets` 恢复归属两项确认）；AC9 事件腿按 TS-first 先例视为覆盖（`generateAssets`/`lang` 已在 TS goldens；`Assets` binding/`generatedLangs()` 面仍无覆盖，需记录口径）；AC3 本域 golden/回读 fixture 与 AC6/7/8 真机/dist/capability 仍需补（owner 33/31/32 已关，需改派 34/后续轮） |
| 41 | **不可关（需真机证据）** | 0/8；AC8 硬性要求注册 Minecraft MCP smoke；真实点击派发、resize/close/替换、焦点/滚动均未证明；同时是 42/44 blocker 与 48 前置——建议与 42 AC7、44 AC6、43 AC6 合并为一轮 26.2 真机 smoke 会话 |
| 42 | **可关（裁决式，附条件）** | AC1–AC6 附证据勾选且全套绿；唯一缺口 AC7 与 41 真机轮同源——按 23/27"部分满足 + 缺口移交"先例裁决关闭，或将 AC7 并入 41 真机轮后关闭 |
| 43 | **需补小证据** | AC2 按 REGENERATE.md 流程再生成 fake-Adapter golden 并出旧新 diff 供审；AC6 并入 41 真机轮；注意主树 `npm run test:probe-types` 预存失败非本票引入 |
| 44 | **不可关（需后续实现轮）** | AC4 的纹理加载/解码管线（blit + NEKO-6005/6007 发射）与 AC6 真机 smoke 是实现工作不是证据缺口；建议拆后续票或并入 48 前置，先在 Error-Reference 保留 reserved 契约位 |

结构性观察（供排期）：41–44 全部收口（或按先例裁决移交真机腿）后，**48（ready-for-agent）即解锁**；26/29 的多个缺口 owner（31/32/33/09）均已 closed，建议本批裁决时统一改派到 34 真机轮或票据自身后续轮，避免 owner 悬空。

---

*本文件为唯一新增产物（`evidence/2026-09-29-inreview-digest/README.md`）；未触碰主树其他未提交改动，未运行 gradle，未执行任何 git 写操作。*
