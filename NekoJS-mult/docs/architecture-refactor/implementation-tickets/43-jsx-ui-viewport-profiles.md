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
  - **fake 侧通过**；**真实端 partial / not-verified**（未启动真实客户端，owner = 票 41）。
  - golden **未经人工审阅**：新文件由 agent regenerate 后核对，维护者待审。

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

`BUILD SUCCESSFUL`；实测 **226 suites / 1674 tests / 0 failures / 0 errors / 5 skipped**（整合期缺陷修复后）；
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

## code-review 修订（第 2 轮，主 agent 核验后）

主 agent 独立核验后提出 6 项必改 + 3 项建议。逐条处理如下；其中 1/2/3/5 是「契约或报告与实现不符」，
本轮**改实现兑现**而不是降表述，因此 AC1/AC2/AC3 保持 `[x]`。

### 1. AC1 规则文本零生产消费者 → 已兑现

- 根因：`UiSpec.viewportProfileRule()` 当时只有 2 处引用（定义 + 测试），capability docs 是另一段硬编码白话。
- 现修：`CoreManagedApiBootstrap` 的 `ui.viewport-profiles` capability **直接使用** `UiSpec.viewportProfileRule()`；
  该文本由 `ViewportProfile`（阈值）+ `UiResponsiveRules`（设计基准/字号区间）+ `UiProfileOverride.KEYS`（受控键）派生。
- 新增断言 `UiViewportContractTest.resolvedStylesViewIsImmutableAndReflectsTheAppliedOverride` 逐字比对 capability docs
  与 `UiSpec.viewportProfileRule()`，并断言其中含 `640x360` 阈值与 `scrollX` 键。

### 2. AC3 scroll / 文本规格名不副实 → 已实现

- 覆盖键由 15 → **19**：新增 `scrollX`/`scrollY`（滚动策略）与 `font`/`wrap`（补齐文本规格，此前只有 `fontSize`）。
- 新增 `UiProp.SCROLL_X`/`SCROLL_Y`（STATE 类，经 `UiProp.kind()` 链），并登记到 `scroll` primitive；
  `wrap` 从只允许 label 扩展到 label/button/input，使「文本规格可按 profile 覆盖」在真实 primitive 上成立。
- `UiLayoutEngine` 新增第二趟 `applyScroll`：scroll 容器的偏移应用到子树（children 矩形保持内容坐标系，
  clip/inspection 报告视口坐标系）；`leafSize` 现按解析后的 font/fontSize/wrap 测量 label/button/input。
- 覆盖键**逐个按 primitive 校验**（`UiPrimitiveRegistry.validateLayout`）：`scrollY` 用在 `label` 上会显式失败。
- golden 现在冻结 scroll 偏移与文本规格（`scroll=/text=` 列），六档可见不同（如 2 档 scrollY=6、4-6 档 scrollY=12）。

### 3. AC2 错误类型未按契约固定 + golden 未覆盖 min/max/percent → 已修

- 新增 `UiLayoutSpec(textFontSize)` 解析路径统一；`min>max` 经 registry 校验入口一律 `UiFailure.INVALID_LAYOUT`。
- ⚠ **如实说明**：`UiLayoutSpec.fromProps(props, context)` **直接调用**（不经 registry）时，`min>max` 仍是
  `UiLayoutSpec` 紧凑构造抛出的 `IllegalArgumentException`——该路径是 common 内部 API，不是脚本面；
  脚本可达路径（候选校验 → `UiPrimitiveRegistry.validateLayout`）一律是契约的 `UiFailure.INVALID_LAYOUT`。
  测试同时钉住两条路径，避免含糊。
- golden 场景新增 `scroll#scroller`（`width:"50%"` + `minWidth:40` + `maxWidth:160`，2 档覆盖为 `25%`、4 档覆盖 `maxWidth:90`），
  因此 min/max 与 percent 的解析、裁剪与越界行为都进了冻结输出；另加 `UiResponsiveOverrideTest.percentAndMinMaxAreResolvedAndClampedByTheContract`。

### 4. AC5 多 root 一致性漏洞 → 已修

- 根因：`viewportDependent` 是 per-root 粘性标志，A 未读过视口时 resize 走纯重排；而纯重排路径**不再重解析**
  per-root 覆盖——但该路径实际每次调用 `currentViewportContext()`（`UiRoot:390`），因此 per-root 覆盖**本来就会**重解析。
  为把这个事实钉死，新增用例 `resizeReResolvesPerRootViewportOverridesForEveryRoot`：
  同一 facade 下 A（无安全区域）与 B（安全区域 100）在 resize 后分别报不同 profile 与不同可用宽。
- **断言面洞已补**：`UiLayoutInvalidationTest` 新增「Java 侧/inspector 读视口不构成依赖」用例，并新增
  `viewportDependent` 语义说明：只有脚本回调期（render / 事件）读视口才置位，Java 侧读取用 `viewportState()`。
- 说明：`viewport()` 改为按 `scriptPhase` 判定，不再被 inspector 路径污染。

### 5. REPORT 数字与 golden 审阅记录 → 已修

- golden 行数按实测更正（原报 234 → 实测 231，本轮扩到 **273**）。
- REPORT §3.1 补齐审阅记录小节（旧/新行数、纯新增结论、⚠ 维护者待审）。
- AC6 真实端与 golden 审阅状态均按实际标 **partial / not-verified**，owner 写明。

### 6. 零消费者 API 与不实描述 → 已清理

| 项 | 处置 |
|---|---|
| `UiLayoutSpec.scaled()` | 删除（零调用者） |
| `ViewportProfile.numbers()` | 删除（零调用者） |
| `UiProfileOverride.isEmpty()` | 删除（零调用者） |
| `UiViewportState` 的 `profile()`/`contentWidth()`/`contentHeight()` default | 删除；调用方改用 `viewport().profile()` 等直接事实 |
| `ViewportContext.Capability.RESPONSIVE_LAYOUT` | 删除（零读取者）；只留 `FIXED_VIEWPORT` |
| `ViewportContext.Capability` / `has()` | **保留并补真实消费者**：`RootUiFacade.resize` 对声明固定视口的 host 提交矛盾逻辑尺寸时抛 `INVALID_LAYOUT`（新增测试 `fixedViewportCapabilityRejectsContradictoryResize`） |
| `UiScreenOptions.layoutCapabilities` / `withLayoutCapabilities` | 删除（只被聚合、从不被读取）；`safeArea` 保留（profile 判定真实使用） |
| `UiLayoutSpec.resolvedViewport()` | 删除（零调用者）；inspector 用 `UiHostInspection.resolvedStyles()` |
| `RootUiFacade.resize(UiSize)` | 删除（新增公开重载无调用者且绕过 `requireOwner`）；只保留 `resize(ViewportContext)` |
| `UiHostInspection.afterRelayout` 的旧值拷贝 | **实现已改**：签名改为接收本次 measure/arrange 重新解析出的 styles/overflow，JavaDoc 与实现一致 |
| 缺省回退的 CSS 类比 | **已改**：删除「与 CSS min-width 同构」的说法，明确写「方向相反」，并在 contract（`UiProfileOverride.forProfile` + `UiResponsiveRules` + capability docs）中说明理由 |

### 建议项

- **覆盖键 3 份平行副本 → 单表派生**：`UiProfileOverride` 重写为 `values` Map + `KEYS` 单表驱动；
  解析分派由 `DIMENSION_KEYS`/`NUMERIC_KEYS`/`BOOLEAN_KEYS` + 一个 switch 完成，`applyTo` 只遍历 `values`；
  类型化访问器全部从同一份 map 派生。新增键只需在 `KEYS` 加一项（并按需归入分类）。
- **声明断链已修**：`UiValueKind.PROFILE_OVERRIDES.tsType` 不再指向未定义的 `NekoJsx.UiProfileOverrides`，
  改为内联 mapped type；`UiJsxDeclarationGenerator` 新增 `UiProfileOverride` 接口，其属性列表**由 `KEYS` 派生**、
  类型文本查 `UiProp.kind()`。测试断言「声明含每个键」且「不含 `NekoJsx.UiProfileOverrides`」。
- **`viewportDependent` 可观测**：语义改按 `scriptPhase` 判定并加测试；长期行为（是否会走重排）由
  `UiLayoutInvalidationTest` 的 render 计数与 host-node 同一性断言钉住。

### 整合期发现的缺陷与修复（fresh checkout 必失败）

**现象**：分支合入 `integration/tickets-08-11-12` 后全量门禁 1 failed —
`UiViewportProfileGoldenTest > sixProfilesProduceStableFakeOutput()`；在 t43 worktree 本地却通过。

**根因**：本票的 `jsx-ui-viewport-profiles.txt` 是本仓唯一 `.txt` golden，而 `.gitattributes` 的
`eol=lf` 列表未覆盖 `*.txt` → Windows（`core.autocrlf=true`）checkout 后工作树变成 CRLF，
而测试用 `Files.readString` 逐字符比较。逐字节证据：本地写出的工作树文件 CR=0（LF）、
仓库 blob CR=0（LF）、缺规则时 checkout 后 **CR=273**；`git check-attr` 对该文件无输出，
而对照 `api-manifest-core.json` 返回 `text: set` / `eol: lf`。

**修复（两个 commit）**：
1. `0b0f3100`（仓库级、单独提交便于审阅）：`.gitattributes` 增加 `*.txt text eol=lf`，与既有 `.json` 分组一致。
2. `2e401899`：golden 测试读/写均归一化行尾（`normalize` 折叠 CRLF 与孤立 CR），
   并新增回归测试 `repositoryAttributesPinsTextGoldenToLf()`（直接读 `.gitattributes`，不依赖 git 可执行文件）。

**验证输出**：修复后 fresh checkout → `bytes=22526 CR=0 LF=273`；
`git ls-files --eol` → `i/lf w/lf attr/text eol=lf`；
强制工作树 CRLF（CR=273）时测试仍 BUILD SUCCESSFUL（归一化保险生效）；
临时移除 `*.txt` 规则并 `--rerun-tasks` → 回归测试 FAILED（能抓住）。

**同类风险自查**：仅 `api-manifest-core.json`（`.json`，已被 `eol=lf` 覆盖）与本票 `.txt` 做逐字符比较；
`probe/**/*.d.ts` 虽有 CRLF 工作树但读者一律 `normalize`，且仓库 blob 为 LF → 不构成缺陷。
**除本票外没有其它不匹配 `eol=lf` 的逐字符比较 golden。**

**AC6 判定不变**：fake 侧通过；golden 人工审阅仍未闭环（owner = 维护者），真实端 owner = 票 41。
本缺陷属于「跨机器必失败」的实现缺陷，已修复并有回归保护，**不是**环境问题。