# 证据：六档 Viewport Profile 与响应式布局（票 43）

> 执行者：dsh-agent。分支 `ticket-43-jsx-ui-viewport-profiles`（worktree `.worktrees/t43/NekoJS-mult`）。
> 本目录只记录**已实际执行**的验证；未执行项在「未验证项」明确列出。
> 全部证据来自 common 的 fake host Adapter；**未**启动真实 Minecraft 客户端。

## 1. 验证命令与结果

| 命令 | 结果 |
|---|---|
| `:common:test --rerun-tasks --no-build-cache :common:check guardLint` | BUILD SUCCESSFUL（16 tasks executed） |
| 测试计数（由 `common/build/test-results/test/TEST-*.xml` 实测，code-review 修订后） | **226 suites / 1673 tests / 0 failures / 0 errors / 5 skipped** |
| `:common:check`（含 `checkCommonIsolation`） | BUILD SUCCESSFUL |
| `guardLint` | BUILD SUCCESSFUL；守卫块 **248** / 扫描 **331 文件** / 违规 **0** / 警告 **0**（与票 40 改前同） |

`--rerun-tasks --no-build-cache` 为全量真跑。

## 2. 本票新增/修改的测试（core/ui，共 14 个类）

| 测试类 | @Test | 覆盖 AC |
|---|---|---|
| `core/ui/UiViewportProfileTest` | 11 | AC1（判定规则/边界/优先级/tie-break/可观察输出） |
| `core/ui/UiResponsiveOverrideTest` | 15 | AC2、AC3、AC4（含 percent/min-max 解析与裁剪） |
| `core/ui/UiLayoutInvalidationTest` | 8 | AC5（只重排不重跑脚本、溢出诊断、多 root per-root 覆盖、脚本阶段才构成依赖） |
| `core/ui/UiViewportProfileGoldenTest` | 3 | AC6（六档 fake golden） |
| `core/ui/UiViewportContractTest` | 8 | AC1/AC2/AC6（契约、声明、规则文本进 capability、固定视口消费者） |
| `core/ui/UiVNodeContractTest`（扩展） | 12 | 布局属性数 14 → 16 |
| `core/ui/UiStateOwnerTest`（扩展） | 8 | FakeRoot 补齐 `viewportState()` |
| `core/api/NormativeApiContractOwnerTest`（扩展） | 6 | 双记账守卫的 facade 过滤谓词同步 |

门禁实测总数 1659 → **1673**（code-review 修订后；首次交付时为 1669）。

## 3. 冻结面（golden）变更——需维护者审阅

### 3.1 新增：`common/src/test/resources/nekojs/golden/jsx-ui-viewport-profiles.txt`

**纯新增，0 删除**（此前不存在）。内容为六个代表视口下 fake Adapter 的完整可观察输出：

| 档位 | 逻辑视口 | guiScale | 溢出诊断 |
|---|---|---|---|
| `desktop-wide` (1) | 900x520 | 2 | 无 |
| `desktop` (2) | 520x340 | 1 | 无 |
| `tablet` (3) | 380x260 | 3 | `panel#fixed` h=36 |
| `handheld` (4) | 280x200 | 1 | `panel#fixed` h=136 |
| `compact` (5) | 200x140 | 2 | `panel#fixed` h=216 |
| `minimal` (6) | 160x110 | 4 | `panel#fixed` h=244 |

每块包含：判定输入与结果（profile/logical/content/safeArea/guiScale/capabilities）、`layoutRevisions`、
每个可见节点的**最终解析样式**（含 profile 覆盖后的 width/height/min/max/margin/padding/gap/align/
justify/anchor/grow/visible/direction/contentScale）、**最终矩形**、**裁剪矩形**与**溢出诊断**。

**变更原因**：AC6 要求「六个 profile 均有稳定 fake 输出」。该文件是本票引入的新冻结基线，
不是对既有 baseline 的改写。

**审阅记录**：

- 旧文件不存在 → 纯新增，**无任何既有 golden 被修改**（初版 231 行；code-review 修订后 273 行，详见 §8.4）。
- 六个档位的输出块以 `rects:` 段为指纹互不相同（`reportCoversAllSixProfilesWithDistinctOutput` 断言）。
- **未**使用任何 Minecraft 类型；断言全部来自公开观察面。
- **旧/新差异**：旧文件不存在 → 新文件 273 行、删除 0 行；无任何既有 golden 行被改写。
- **审阅记录**：⚠ 本 golden 变更**未被人工审阅**，仅由 agent 以 `-Dnekojs.golden.regenerate=true`
  再生成并逐块核对（六档 `rects:` 段互不相同由测试断言）。**维护者待审标记：未审阅**。
  对应 AC6 因此记为 **partial**（fake 侧 pass / 审阅与真实端未闭合），owner = 维护者（审阅）与票 41（真实端）。

### 3.2 **未**改动：`api-manifest-core.json`

票 43 **没有**修改既有 API 表面冻结基线。原因与决定：

- 票 43 的响应式视口语汇（`ViewportProfile`/`ViewportContext`/`ProjectedViewport`/
  `UiProfileOverride`/`UiLayoutSpec`/`UiViewportState`）经 **`UiSpec.CAPABILITY_IDS` 的能力声明 +
  JSX/Probe 声明**进入公开 contract，而不是新增 `symbols`。这与票 40 对
  `UiRootHandle`/`UiLayoutSpec` 的既有权衡一致。
- 契约能力新增一条 `ui.viewport-profiles`（`SUPPORTED`），由
  `UiContractChainTest.uiCapabilitiesAreDeclaredInTheContract` 与 `UiViewportContractTest` 钉住。
- **后果（如实记录）**：`ViewportProfile`/`ViewportContext` 等类型**不在** frozen symbol 面上，
  因此 managed declaration 的 `$UiProp` 风格声明里没有它们；脚本可见性由 JSX 声明
  （`UiJsxDeclarationGenerator`）与 inspector record 承载。若后续票（45 inspector / 47 AI
  authoring docs）需要它们出现在 managed symbol 面，必须走 golden 的
  `-Dnekojs.golden.regenerate=true` + 维护者审阅流程（本票不代答）。

## 4. 逐 AC 判定

| AC | 判定 | 主要机制 | 证据 |
|---|---|---|---|
| AC1 判定规则/边界/优先级/tie-break/可观察输出进 contract；GUI scale 不等同 profile | **通过** | `ViewportProfile`（六档阈值单点持有）+ `UiResponsiveRules.profileOf`（唯一判定入口）+ `ViewportContext`（逻辑视口/安全区域/guiScale/能力）+ `ProjectedViewport`（可观察输出）；`UiSpec.viewportProfileRule()` 把规则文本写进 capability | §3.2；`UiViewportProfileTest` 11 项；`UiViewportContractTest` |
| AC2 解析顺序/缺省回退/非法 profile/非法比例/min-max 裁剪规则固定 + fake golden | **通过** | `UiProfileOverride.parseAll/forProfile/applyTo`；`UiLayoutSpec.fromProps(UiProps, ViewportContext)` 单点解析；`UiPrimitiveRegistry.validateLayout` 在候选阶段校验**每一条**覆盖 | `UiResponsiveOverrideTest`；`UiViewportProfileGoldenTest` |
| AC3 row/column、stack、scroll、spacing、padding、align、anchor、可见性和文本规格可按 profile 覆盖；窄/宽屏重排不必重复六遍 | **通过（code-review 修订后补齐 scroll 与文本规格）** | `UiProp.DIRECTION` + `UiProp.PROFILES`；`UiAxis`；覆盖键 15 个（含 direction/fontSize/visible）；回退规则用「声明作用于本档及更窄档」实现断点式重排 | `UiResponsiveOverrideTest.directionOverride…`、`rowReordersIntoColumn…`、`visibilityCanBeOverriddenPerProfile` |
| AC4 设计坐标/逻辑像素/连续比例共存；字号不被强制整体等比无限缩放 | **通过** | `UiDimension.Kind.DESIGN`（`"80dp"`）+ `UiResponsiveRules.contentScale`（`min(1, contentWidth/640)`）；`resolveFontSize = clamp(size × sqrt(scale), 6, 24)` | `UiResponsiveOverrideTest.designPixels…`、`fontSizeIsClampedAndGrowsSlower…`、`labelMeasurementUsesTheResolvedFontSize` |
| AC5 resize/profile 切换只使布局失效并重新 measure/arrange，不重跑 GraalJS render 或重建全部 host node；稳定输出含最终矩形、裁剪和溢出诊断 | **通过（fake 侧）** | `UiRoot.invalidateLayout()`：脚本 render 未读过视口 → 纯重排；读过 → 退回 reconcile（`viewportDependent`）。`inspection` 新增 `resolvedStyles`/`overflow`/`viewport`/`layoutRevisions` | `UiLayoutInvalidationTest` 6 项 |
| AC6 六个 profile 均有稳定 fake 输出；真实 NeoForge 26.2 resize smoke 在 41 完成后补入同一 contract | **partial** | 六档 fake golden 完成并冻结 | §3.1；真实端见 §6 |

## 5. 与票 40 的契约兼容性

- `UiLayoutSpec`/`UiDimension`/`UiHostInspection` 均为**扩展**（新增 record 分量 / 新增静态工厂），
  既有构造调用点（`UiRoot`、`UiLayoutEngine`、测试夹具）已同步；**未改变既有语义**。
- `UiLayoutSpec.props()` 由 14 条扩到 16 条（新增 `direction`/`profiles`），
  `UiVNodeContractTest.layoutPropsVocabularyStaysInSyncWithLayoutSpec` 同步断言 16。
- `UiProp.kind()` 仍是属性语义唯一事实源：新增属性各只加一行 `UiProp` + 一个 `UiValueKind` 分支，
  取值校验与 TS 声明自动同步。
- 未新增第二份属性表：`UiProfileOverride.KEYS` 是**布局覆盖包内部**的受控键清单，由本票解析器
  唯一消费；节点属性词表仍只有 `UiProp`。
- 重排语义的**唯一权威**是 `UiLayoutEngine`；`UiProfileOverride`/`UiLayoutSpec` 只描述约束。

## 6. 未验证项（如实记录）

- **真实 Minecraft 客户端 / NeoForge 26.2 resize smoke**：本票范围内**未**做任何真实客户端尝试，
  **未**使用 Minecraft MCP，也**未**运行 `runGameTestServer`。AC6 的真实端据此记为
  **partial / not-verified**，owner：**票 41**（NeoForge Screen Adapter）完成后回填同一公开
  contract（`UiRootHandle.viewportState()` / `inspection()`），断言不得使用私有 widget 布局。
- **`UiResources.viewportContext()` 的真实装配值**：本票只定义契约与缺省实现（由 `viewport()`
  派生：无安全区域 / guiScale=1 / 无额外能力）。真实客户端把安全区域、原生 GUI scale 与 host
  布局能力送进判定，归票 41。
- **未实现**：`scroll` 的滚动偏移/裁剪策略按 profile 变化（票 40 已撤下 `scrollX/scrollY/
  `scrollable`，本票未复引入）；`justify: space-between/around` 的分布。
- **未做性能基线**：resize 重排的开销未测（独立 P0 工作项）。

## 7. 本票的历史（如实记录）

前两次执行进程失败、零产出；本票为**重试第 3 次**。本轮按 AC 分小步提交，
每步都跑过 `core/ui` 定向测试。

---

## 8. code-review 修订（第 2 轮，主 agent 核验后）

主 agent 逐条实测后判定：核心方向正确，但 **6 项必改**。本轮**改实现兑现**，未降低表述。

### 8.1 修订后的门禁（重跑）

| 命令 | 结果 |
|---|---|
| `:common:test --rerun-tasks --no-build-cache :common:check guardLint` | **BUILD SUCCESSFUL** |
| 测试计数 | **226 suites / 1673 tests / 0 failures / 0 errors / 5 skipped**（改前 1669） |
| `guardLint` | 248 守卫 / 331 文件 / 0 违规 / 0 警告（未变） |
| `:common:check`（含 `checkCommonIsolation`） | 通过 |

`core/ui` 计数：`UiLayoutInvalidationTest` 6 → 8、`UiResponsiveOverrideTest` 14 → 15、`UiViewportContractTest` 7 → 8。

### 8.2 逐条处理

| # | 问题 | 处置 | 证据 |
|---|---|---|---|
| 1 | `UiSpec.viewportProfileRule()` 零生产消费者，capability docs 是另一段硬编码白话 → 「规则文本进契约」不实 | **改实现**：capability docs **直接使用**该规则文本；文本由 `ViewportProfile` 阈值 + `UiResponsiveRules`（设计基准/字号区间）+ `UiProfileOverride.KEYS` 派生 | `CoreManagedApiBootstrap:447-457`；`UiViewportContractTest` 逐字比对 docs 与 `viewportProfileRule()`，并断言含 `640x360` 与 `scrollX` |
| 2 | AC3 名不副实：覆盖键无 scroll，文本规格只有 fontSize | **改实现**：键 15 → 19，新增 `scrollX`/`scrollY`/`font`/`wrap`；新增 `UiProp.SCROLL_X`/`SCROLL_Y` 并登记到 `scroll`；`wrap` 扩到 label/button/input；`UiLayoutEngine.applyScroll` 第二趟应用偏移；`leafSize` 按解析后文本规格测量；覆盖键按 primitive 校验 | `UiProfileOverride.KEYS`；`UiLayoutEngine:applyScroll/leafSize`；golden 的 `scroll=`/`text=` 列 |
| 3 | `min>max`/percent 越界是 `IllegalArgumentException` 而非契约的 `UiFailure.INVALID_LAYOUT`；golden 无 min/max/percent | **部分改实现 + 如实说明**：脚本可达路径（registry 校验）一律 `INVALID_LAYOUT`；golden 场景新增 `width:"50%"`+`minWidth:40`+`maxWidth:160`（2 档覆盖 25%、4 档覆盖 maxWidth:90）。⚠ `UiLayoutSpec.fromProps(props, context)` **直接调用**（common 内部 API，非脚本面）仍抛 IAE，测试同时钉住两条路径 | golden `#scroller` 六档矩形/溢出；新增 `percentAndMinMaxAreResolvedAndClampedByTheContract` |
| 4 | 多 root：B 读视口后 resize，A 的 per-root 覆盖可能不重解析；断言面洞 | **改实现 + 补断言**：新增 A（无安全区域）/B（安全区域 100）用例，resize 后两者报不同 profile 与不同可用宽；`viewportDependent` 改按 `scriptPhase` 判定（Java/inspector 读取不置位），并新增「Java 侧读视口不触发脚本重跑」用例 | `resizeReResolvesPerRootViewportOverridesForEveryRoot`、`viewportReadsAreTrackedOnlyDuringScriptCallbacks` |
| 5 | REPORT 行数错（234 实际 231）；缺 golden 审阅记录 | **改报告**：行数按实测（本轮扩到 **273**）；§3.1 补齐审阅记录；AC6 真实端与 golden 审阅按实际标 partial/not-verified | §3.1、§6 |
| 6 | 零消费者 API + 两处不实描述 | **删除**：`UiLayoutSpec.scaled()`、`ViewportProfile.numbers()`、`UiProfileOverride.isEmpty()`、`UiViewportState.profile/contentWidth/contentHeight` 三个 default、`Capability.RESPONSIVE_LAYOUT`、`UiScreenOptions.layoutCapabilities/withLayoutCapabilities`、`UiLayoutSpec.resolvedViewport()`、`RootUiFacade.resize(UiSize)`。**补真实消费者**：`Capability.FIXED_VIEWPORT` 由 `RootUiFacade.resize` 消费（矛盾 resize 抛 `INVALID_LAYOUT`，新增测试）。**改实现**：`UiHostInspection.afterRelayout` 改为接收本次重新解析出的 styles/overflow（不再复用旧拷贝）。**去错误类比**：删除「与 CSS min-width 同构」，明确写方向相反并给理由 | 全仓 grep 上述符号零命中（除定义/测试）；`fixedViewportCapabilityRejectsContradictoryResize` |

### 8.3 建议项

- **覆盖键单表派生**：`UiProfileOverride` 重写为 `values` Map + `KEYS` 单表；解析由 `DIMENSION_KEYS`/`NUMERIC_KEYS`/`BOOLEAN_KEYS` 分类 + 单 switch 分派，`applyTo` 只遍历 `values`；类型化访问器同源派生。新增键只改一处。
- **声明断链修复**：`UiValueKind.PROFILE_OVERRIDES.tsType` 改为内联 mapped type；`UiJsxDeclarationGenerator` 新增由 `KEYS` 派生的 `UiProfileOverride` 接口，键类型查 `UiProp.kind()`；测试断言「含每个键」且「不含 `NekoJsx.UiProfileOverrides`」。
- **`viewportDependent` 可观测**：语义按 `scriptPhase` 判定并加测试；重排与否由 render 计数与 host-node 同一性断言钉住。

### 8.4 修订后的 golden（新文件）

- `jsx-ui-viewport-profiles.txt`：231 行 → **273 行**（新增 `scroll#scroller` 子树、`scroll=`/`text=` 列）。
- 六档现在同时冻结：profile/输入事实、**percent 宽度**、**min/max**、**scroll 偏移**、**文本规格**、矩形、裁剪与溢出。
- ⚠ 仍**未经人工审阅**；维护者待审。