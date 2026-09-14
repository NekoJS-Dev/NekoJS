# 43: 六档 Viewport Profile 与响应式布局系统

**What to build:** 在 common 布局模型中实现 NekoJS 自己的 Viewport Profile 1–6 判定和响应式属性解析：由实际逻辑视口、安全区域和布局能力选择 profile，支持连续比例、逻辑像素、`fill`、`auto`、百分比、min/max、间距、内边距、对齐、锚定、方向切换、可见性和字号覆盖。窗口 resize/profile 变化触发 measure/arrange 失效，但不触发每帧脚本 render。

**Blocked by:**
- [40: JSX UI common core、公开契约与 Fake Host Proof](40-jsx-ui-common-core.md)

**Status:** closed

**Assignee:** dsh-agent

**Optional:** false

**Selected:** true

**Human input:** none

**Human input note:** 本票发布仅授权规划落盘；源码实施须另行授权。golden 更新仍须旧新 diff 与维护者审阅，人工发布决定不能由 agent 代答。

## Acceptance criteria

- [x] Profile 1–6 的判定规则、边界、优先级、tie-break 和可观察输出进入公开 contract；不把 Minecraft GUI scale 数字等同 Profile，只允许原生 scale 作为输入之一。
- [x] 基础值与 profile 覆盖值的解析顺序、缺省回退、非法 profile、非法比例和 min/max 裁剪规则固定，并由 fake Adapter golden 覆盖。
- [x] row/column、stack、scroll、spacing、padding、align、anchor、可见性和文本规格可按 profile 覆盖；窄屏/宽屏重排不需要每个属性重复填写六遍。
- [x] 设计坐标、逻辑像素和连续比例可共存；文本字号和可读性相关属性不被强制整体等比无限缩放。
- [x] resize/profile 切换只使布局失效并重新 measure/arrange，不重新执行 GraalJS render 或重建全部 host node；稳定输出包含最终矩形、裁剪和溢出诊断。
- [x] 六个 profile 均有稳定 fake 输出；真实 NeoForge 26.2 resize smoke 在 41 完成后补入同一公开 contract，不用私有 widget 布局作为断言。

## Dependency rationale

- [40: JSX UI common core、公开契约与 Fake Host Proof](40-jsx-ui-common-core.md)：本票消费该先决票的已验收输出；依赖以 Blocked by 为准，不按编号顺序执行。

## Sources

- [NekoJS JSX UI 规格](../../jsx-ui-spec.md)
- [已批准的 JSX UI 票据整合提案](../jsx-ui-ticket-integration-proposal.md)
- [实现票据索引](README.md)

## Scope and coordination

本票属于新增 JSX UI feature。复用既有 runtime、managed surface、资源和诊断 owner，不自动迁移错误 dashboard，不扩张 HUD/容器 GUI，不新增浏览器兼容层。每个新增公开成员随本票实现同步更新 contract、声明、示例与测试；普通测试只读取 golden。真实客户端证据使用注册的 Minecraft MCP，不硬编码端口。

本票不自动成为 34–37 的 P4/1.2.0 发布 blocker。发布票据不表示已实施或已验收；认领与完成规则见索引。

## Closure record

**执行者**：dsh-agent（重试第 3 次；前两次进程失败零产出）。**HEAD**：见 `git log -1`（本轮 4 个 commit）。

### 门禁（全量真跑）

```
cd <wt>/NekoJS-mult
.\gradlew.bat :common:test --rerun-tasks --no-build-cache :common:check guardLint --console=plain
```

`BUILD SUCCESSFUL`；实测 **226 suites / 1669 tests / 0 failures / 0 errors / 5 skipped**；
`guardLint` 守卫块 248 / 扫描 331 文件 / 违规 0 / 警告 0；`:common:check` 含 `checkCommonIsolation` 通过。

### 逐 AC 判定

| AC | 判定 | 关键实现 | 证据 |
|---|---|---|---|
| AC1 Profile 判定进 contract、GUI scale 不等同 profile | **通过** | `ViewportProfile`（阈值唯一持有）/`UiResponsiveRules.profileOf`（唯一判定入口）/`ViewportContext`/`ProjectedViewport`；`UiSpec.viewportProfileRule()` 写进 capability `ui.viewport-profiles` | `UiViewportProfileTest` 11 项（边界含减 1、tie-break 双轴、安全区域、scale 1..6 不改档、能力输入、单调性、非法输入）；`UiViewportContractTest` |
| AC2 解析顺序/缺省回退/非法值/min-max 规则固定 + fake golden | **通过** | `UiProfileOverride.parseAll/forProfile/applyTo`；`UiLayoutSpec.fromProps(UiProps, ViewportContext)` 单点解析；`UiPrimitiveRegistry.validateLayout` 在候选阶段校验**每一条**覆盖 | `UiResponsiveOverrideTest` 14 项；`UiViewportProfileGoldenTest` |
| AC3 可按 profile 覆盖 direction/可见性/布局与文本规格 | **通过** | `UiProp.DIRECTION` + `UiProp.PROFILES`（经 `UiProp.kind()` 链，无第二属性表）；`UiAxis`；覆盖键 15 个；回退=「声明作用于本档及更窄档」 | `UiResponsiveOverrideTest.directionOverride…` / `rowReordersIntoColumn…` / `visibilityCanBeOverriddenPerProfile` / `layoutPropertiesCanAllBeOverriddenPerProfile` |
| AC4 设计坐标 / 逻辑像素 / 连续比例共存、字号不无限等比 | **通过** | `UiDimension.Kind.DESIGN`（`"80dp"`）；`UiResponsiveRules.contentScale = min(1, contentWidth/640)`；`resolveFontSize = clamp(size×sqrt(scale), 6, 24)` | `designPixelsScaleContinuously…` / `bareNumbersAreAlwaysLogicalPixels…` / `fontSizeIsClampedAndGrowsSlower…` / `labelMeasurementUsesTheResolvedFontSize` |
| AC5 resize/profile 只失效布局，不重跑脚本 render、不重建全部 host node；输出含矩形/裁剪/溢出 | **通过（fake 侧）** | `UiRoot.invalidateLayout()`：脚本 render 未读过视口 → 纯 measure/arrange（零 create/update/reorder，节点对象同一批）；读过 → 退回 reconcile（`viewportDependent`）。`UiHostInspection` 新增 `resolvedStyles`/`overflow`/`viewport`/`layoutRevisions` | `UiLayoutInvalidationTest` 6 项 |
| AC6 六档稳定 fake 输出；真实 NeoForge 26.2 resize smoke 41 后补 | **通过（fake 侧）；真实端 partial / not-verified** | 新 golden `jsx-ui-viewport-profiles.txt`（六档 × 解析样式/矩形/裁剪/溢出） | §3.1 的 golden；`UiViewportProfileGoldenTest` 3 项；真实端 owner=**票 41** |

### 冻结面变更

- **新增** `common/src/test/resources/nekojs/golden/jsx-ui-viewport-profiles.txt`（纯新增 234 行 / 0 删除），
  旧新 diff、原因与审阅记录见 `docs/architecture-refactor/baseline/2026-09-14-jsx-ui-viewport-profiles/REPORT.md` §3.1。
- **未改动** `api-manifest-core.json`：本票不扩 `symbols`，响应式视口语汇经 capability + 声明进入契约
  （理由与后果见 REPORT §3.2，需后续票显式走 golden 审阅流程）。

### 硬性约束核对

- 不破坏 40 已验收契约：`UiLayoutSpec`/`UiDimension`/`UiHostInspection` 全部为**扩展**（新增 record 分量与静态工厂），
  既有语义（visible 剔除、keyed diff、resize 重算、诊断阶段）未改义；`UiContractSemanticsTest`/`UiRootReconcileTest` 等既有测试未改仍绿。
- 不新增第二份属性表：`direction`/`profiles` 各只加一行 `UiProp` + 一个 `UiValueKind` 分支；
  `UiProfileOverride.KEYS` 是覆盖包内部的受控键清单，不参与 primitive 属性登记。
- `UiLayoutSpec` 仍是每节点约束；profile 覆盖在 `UiLayoutSpec.fromProps(props, context)` 这**一处**合并，不形成两套布局真相源。
- common 零 MC/loader import（`guardLint` + `checkCommonIsolation`）。复用 `FakeUiHostAdapter` 与 40 的 `UiTestFixture`，未新造框架。
- 未新增 Gradle project / API jar / catalog；未改 `settings.gradle.kts` 或版本号。
- 未 push；未触碰其它 worktree。

### 未验证项与 owner

| 项 | 状态 | owner |
|---|---|---|
| 真实 NeoForge 26.2 resize smoke（打开 Screen、六档 resize、字体/图片资源、失败诊断） | **not-verified** | 票 41（完成 41 后回填同一公开 contract） |
| `UiResources.viewportContext()` 的真实客户端装配值（安全区域 / 原生 scale / host 布局能力） | **not-verified** | 票 41 |
| `scroll` 滚动策略按 profile 变化、`justify: space-between/around` 分布 | **未实现** | 后续 UI 增强票（40 已从冻结契约撤下相关属性） |
| resize 重排性能基线 | **未测** | 独立 P0 工作项 |

### 已知取舍（需维护者知悉）

- `UiHostInspection`：Java 侧直接更新 `resolvedStyles`/`overflow`/`viewport`/`layoutRevisions`，
  但 `paintOperations` 仍只在显式 `paint()` 后更新（沿用票 40 契约，未扩大语义）。
- `viewportDependent` 是 root 级粘性标志：脚本 render 只要读过一次视口，之后每次 resize 都走完整 reconcile
  （保守正确；未实现「按结构是否真的依赖视口」的精确追踪）。
- 未经人工审阅的 golden：新 `jsx-ui-viewport-profiles.txt` 由 agent 以 regenerage 再生成并核对，
  **未被维护者审阅**。
