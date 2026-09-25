# 47: AI UI Authoring Contract 与转换 Cookbook

**What to build:** 交付面向 AI 的 UI authoring 文档：以已发布 declaration/Probe 输出、primitive catalog、真实事件对象、signal/store 语义、Profile 规则、资源规则和 Inspector record 为事实源，说明如何生成、自检、诊断和局部修正 JSX。文档包含网页转换 cookbook、禁令、输入清单、输出结构、unsupported 报告格式和 Inspector 修正协议。

**Blocked by:**
- [40: JSX UI common core、公开契约与 Fake Host Proof](40-jsx-ui-common-core.md)
- [45: UI Inspector、布局测量与截图差异基线](45-jsx-ui-inspector.md)
- [46: AI-assisted 网页转换映射与输入契约](46-jsx-ui-web-conversion-contract.md)

**Status:** closed（全部 AC 已勾选；维护者授权见文末 Maintainer sign-off（2026-09-25））

**Assignee:** workbuddy-kimi-47（main-session agent；mult worktree）

**Claim record（2026-09-25）:** 认领时 40/45/46 均为 `in-review`（交付完成、维护者审阅中，未 closed）。照票 46/42/44/45 先例记录 blocker 偏差并继续：用户直接指令「领取一张票开始做」（@skill:implement，2026-09-25）构成源码与文档实施的另行授权。本票交付物为文档与其可执行 fixture，无 golden 更新；六档 Inspector golden 既有，不动。

**Optional:** false

**Selected:** true

**Human input:** none

**Human input note:** 本票发布仅授权规划落盘；源码实施须另行授权。golden 更新仍须旧新 diff 与维护者审阅，人工发布决定不能由 agent 代答。

## Acceptance criteria

- [x] 文档中的每个 primitive、props、事件字段、状态 API、资源标识和 capability 均引用或生成自已冻结 managed declaration / Probe 输出；不存在仅存在于文档的 aspirational API。【evidence: §1 fact sources 表列 12 项冻结事实源（jsx-runtime.ts、生成的 managed declaration、probe golden、fake-host proof、authoring proof fixture、UiColor/UiResourceId/VisualStyleResolver/UiResourceResolver/UiDiagnostic/GenerationGlobals/Inspector 契约）；Spec 轴评审逐项抽查 `UI.primitives/element/batch/profileFor/createRoot/createSignal/createStore`、`root.dispatch/refresh/resize/close/isDisposed/layout` 均在导出表（jsx-runtime.ts:227-234, 1061-1164），9 事件名、11 primitives、profile 档位（320×180…1280×720）与源码一致，颜色/资源语法与 UiColor/UiResourceId 一致，NEKO-6001..6007 与 UiErrorCodes 一致（6005/6007 如实标注 reserved）；未发现仅存在于文档的 API】
- [x] 每个 primitive 条目包含用途、children 规则、props 类型、默认值、布局/视觉行为、事件对象、profile 行为、常见错误和最小示例。【evidence: 11 条目（§5.1–§5.11）现均带全部九个 facet；默认值集中在 §5.0 共享表（label 的 fontSize 默认 9 单列）；review 补强为 screen/panel/row/column/stack/scroll/label/button/input/image/spacer 补齐了缺失的 Events/Profile behavior/Common errors 行】
- [x] AI 自检清单覆盖未知标签、未知 CSS 能力、非法资源、错误线程、每帧 render、key、焦点、滚动、文本可读性、reload 残留 callback 和 conversion uncertainty。【evidence: §7 共 11 项与 AC 清单一一对应，每项给出失败模式与检测方法（runtime 校验/NEKO 码/声明层 typecheck）】
- [x] Inspector 修正协议要求基于 profile、节点矩形、裁剪、resolved style、资源状态和差异报告做局部修正；禁止每次偏差重写整棵 UI。【evidence: §9 七步修正循环（读快照→定位→诊断→diff→最小修复→复测→停止条件），显式禁止整树重写（"Rewriting the whole tree per deviation is prohibited"），操作面为 profile/rect/clip/style/resources/SnapshotDiffer 差异报告】
- [x] 文档明确优先级为结构等价、交互等价、响应式等价、视觉等价，并说明 Minecraft 与浏览器字体/栅格化差异导致不承诺逐像素一致。【evidence: 引言固定优先级 structural > interaction > reactive > visual，并声明字体/GUI 缩放/安全区/栅格化差异 → 永不承诺逐像素；review 补强在首次出现处定义 "reactive"= signal/store 状态传播等价（该词由票 46 的 `reportVersion: 1` schema 冻结），响应式 profile 等价经 §10 verification.profilesExercised 单列验证——术语歧义见 Delivery record 遗留项】
- [x] 文档示例可被 typecheck/contract fixture 执行或验证；示例不使用 unavailable/not verified 能力。【evidence: §11 验证映射；`TypeScriptUiAuthoringDocsTest` 经 fake-host 真执行 §5 全部 11 个最小示例（check id screen-1…spacer-1 逐一断言）+ 两个转换输出（挂载/交互/resize）；probe golden 钉声明层类型；image 示例如实标注 placeholder 绘制与 reserved NEKO-6005/6007】
- [x] 文档不把 AI 辅助转换描述为自动浏览器兼容层、通用编译器或无需验收的一次性生成。【evidence: 引言 "What this document is — and is not" 三条否定式声明逐项排除；cookbook §首与 §8 转换循环末端均要求人工 review】

## Delivery record (2026-09-25)

背景：本票交付物（`ai-authoring-contract.md`、proof fixture、执行测试、probe 类型用例）随 `1136f30a` 批次落地，票 46 随后回填了 §1/§3/§10/§11/§12 并明确"文档头部 Status 行留给 47 处理"。本轮完成认领、七条 AC 逐条验证、双轴 review（Standards + Spec 子代理）与修复。

变更（全部在本票写集内，纯文档与票据，无代码改动）：

- `docs/architecture-refactor/ai-authoring-contract.md`：
  - 头部 Status 行 → `in-review`（兑现票 46 的移交约定）。
  - review 修复（硬性）：删除全仓库唯一的 `{#color-grammar}` 属性锚点（GitHub slug 下 §5.2 链接失效），改为普通标题锚。
  - review 修复（AC5）：首次出现处定义 "reactive" 为 signal/store 状态传播等价，注明该词由票 46 冻结 schema（`reportVersion: 1`）所有，responsive-profile 等价经 verification 记录单列——消除与票文"响应式等价"的读解歧义。
  - review 修复（AC2）：为 11 个 primitive 条目补齐缺失 facet（Events/Profile behavior/Common errors），AC 字面合规。
  - review 修复（措辞）：§1 事实源表"Every example in this document … runs and passes"收窄为"Every section 5 catalog example"（§2 lowering 约束示例未被执行，原表述过度承诺）。
- 本票文件：认领记录、AC 证据、状态 → `in-review`。

Review 记录不改项（判断级，理由在案）：`TypeScriptUiAuthoringDocsTest.boot()` 与 `NekoTypeScriptJsxRuntimeTest` 的 boot 模式重复（提取共享 helper 须改他票测试文件，超本票写集，留作后续重构候选）；proof fixture 自实现 `makeHost()`（ui-core.tsx 的 host 含模块级断言不可直接 import，自包含可辩护）；`readDocsFixture` 相对路径探测仓库根（有 fallback 与断言兜底）。

验证（命令与结果，本地 Windows x64，Gradle 9.6.0 / JDK 25.0.2）：

- 焦点：`./gradlew.bat :common:test --tests "com.tkisor.nekojs.core.module.TypeScriptUiAuthoringDocsTest" --tests "com.tkisor.nekojs.core.module.WebConversionReportContractTest" --tests "com.tkisor.nekojs.core.module.NekoTypeScriptJsxRuntimeTest"` → BUILD SUCCESSFUL（16s，0 失败）。
- 全套（无过滤器）：`:common:test` → 1930 tests / 4 skipped / 0 failures / 0 errors。本票无节点侧代码改动，26.x/1.21.1 套件不受影哝（上一票 45 交付时全套 438/438/313 绿）。

遗留：

- **术语决策留给维护者**：票文"响应式等价"在交付文档中为 "reactive"（signal/store 语义，与票 46 冻结 schema 一致）。若维护者认定票意是 viewport-responsive，则票 46 的 equivalence 键需 bump `reportVersion` 改名——超出 agent 权限，已在文档首次出现处显式定义以消除读解歧义。
- 真实客户端证据属 41/48 范围；本票示例验证全部在 fake-host seam 完成。
- 未触碰任何 golden（六档 Inspector golden、probe golden 均只读）。
- 验证仅在本地 Windows 环境完成；CI 与五节点矩阵未运行。

## Maintainer sign-off（2026-09-25）

维护者授权（会话原文“那你看着来关闭，你自己看是否可以关闭”）指示由 agent 依索引规则 6 判定可关闭票。据此复核：本票 7/7 AC 已勾选且逐项附证据（全部 primitive/props/事件/状态/资源/capability 均引自冻结事实源、11 个 primitive 条目九 facet 齐备、自检清单 11 项、Inspector 七步局部修正协议、等价优先级与不承诺逐像素、示例经 `TypeScriptUiAuthoringDocsTest` 真执行、不宣称自动浏览器兼容层），交付物随 `1136f30a` 落地并在本票补齐 review 修复，验证见 Delivery record。

遗留项如实保留：「响应式等价 / reactive」术语裁定与票 46 `reportVersion` 改名属维护者决策，已在文档首次出现处显式定义以消除歧义；真实客户端证据属 41/48；未触碰任何 golden。据此 Status 转 `closed`。

## Dependency rationale

- [40: JSX UI common core、公开契约与 Fake Host Proof](40-jsx-ui-common-core.md)：本票消费该先决票的已验收输出；依赖以 Blocked by 为准，不按编号顺序执行。
- [45: UI Inspector、布局测量与截图差异基线](45-jsx-ui-inspector.md)：本票消费该先决票的已验收输出；依赖以 Blocked by 为准，不按编号顺序执行。
- [46: AI-assisted 网页转换映射与输入契约](46-jsx-ui-web-conversion-contract.md)：本票消费该先决票的已验收输出；依赖以 Blocked by 为准，不按编号顺序执行。

## Sources

- [NekoJS JSX UI 规格](../../jsx-ui-spec.md)
- [已批准的 JSX UI 票据整合提案](../jsx-ui-ticket-integration-proposal.md)
- [实现票据索引](README.md)

## Scope and coordination

本票属于新增 JSX UI feature。复用既有 runtime、managed surface、资源和诊断 owner，不自动迁移错误 dashboard，不扩张 HUD/容器 GUI，不新增浏览器兼容层。每个新增公开成员随本票实现同步更新 contract、声明、示例与测试；普通测试只读取 golden。真实客户端证据使用注册的 Minecraft MCP，不硬编码端口。

本票不自动成为 34–37 的 P4/1.2.0 发布 blocker。发布票据不表示已实施或已验收；认领与完成规则见索引。
