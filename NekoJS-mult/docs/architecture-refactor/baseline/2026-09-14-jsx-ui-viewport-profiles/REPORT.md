# 证据：六档 Viewport Profile 与响应式布局（票 43）

> 执行者：dsh-agent。分支 `ticket-43-jsx-ui-viewport-profiles`（worktree `.worktrees/t43/NekoJS-mult`）。
> 本目录只记录**已实际执行**的验证；未执行项在「未验证项」明确列出。
> 全部证据来自 common 的 fake host Adapter；**未**启动真实 Minecraft 客户端。

## 1. 验证命令与结果

| 命令 | 结果 |
|---|---|
| `:common:test --rerun-tasks --no-build-cache :common:check guardLint` | BUILD SUCCESSFUL（16 tasks executed） |
| 测试计数（由 `common/build/test-results/test/TEST-*.xml` 实测） | **226 suites / 1669 tests / 0 failures / 0 errors / 5 skipped** |
| `:common:check`（含 `checkCommonIsolation`） | BUILD SUCCESSFUL |
| `guardLint` | BUILD SUCCESSFUL；守卫块 **248** / 扫描 **331 文件** / 违规 **0** / 警告 **0**（与票 40 改前同） |

`--rerun-tasks --no-build-cache` 为全量真跑。

## 2. 本票新增/修改的测试（core/ui，共 14 个类）

| 测试类 | @Test | 覆盖 AC |
|---|---|---|
| `core/ui/UiViewportProfileTest` | 11 | AC1（判定规则/边界/优先级/tie-break/可观察输出） |
| `core/ui/UiResponsiveOverrideTest` | 14 | AC2、AC3、AC4 |
| `core/ui/UiLayoutInvalidationTest` | 6 | AC5（只重排不重跑脚本、溢出诊断） |
| `core/ui/UiViewportProfileGoldenTest` | 3 | AC6（六档 fake golden） |
| `core/ui/UiViewportContractTest` | 7 | AC1/AC2/AC6（契约与声明） |
| `core/ui/UiVNodeContractTest`（扩展） | 12 | 布局属性数 14 → 16 |
| `core/ui/UiStateOwnerTest`（扩展） | 8 | FakeRoot 补齐 `viewportState()` |
| `core/api/NormativeApiContractOwnerTest`（扩展） | 6 | 双记账守卫的 facade 过滤谓词同步 |

门禁实测总数 1659 → **1669**；其中 `core/ui` 新增 @Test 41 个。

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

- 旧文件不存在 → 纯新增，**无任何既有 golden 被修改**。
- 六个档位的输出块以 `rects:` 段为指纹互不相同（`reportCoversAllSixProfilesWithDistinctOutput` 断言）。
- **未**使用任何 Minecraft 类型；断言全部来自公开观察面。
- ⚠ 本 golden 变更**未被人工审阅**，仅由 agent 以 `-Dnekojs.golden.regenerate=true` 再生成
  并逐块核对；请维护者核对后再接受。

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
| AC3 row/column、stack、scroll、spacing、padding、align、anchor、可见性和文本规格可按 profile 覆盖；窄/宽屏重排不必重复六遍 | **通过** | `UiProp.DIRECTION` + `UiProp.PROFILES`；`UiAxis`；覆盖键 15 个（含 direction/fontSize/visible）；回退规则用「声明作用于本档及更窄档」实现断点式重排 | `UiResponsiveOverrideTest.directionOverride…`、`rowReordersIntoColumn…`、`visibilityCanBeOverriddenPerProfile` |
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
