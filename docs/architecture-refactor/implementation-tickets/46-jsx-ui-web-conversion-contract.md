# 46: AI-assisted 网页转换映射与输入契约

**What to build:** 定义 AI 辅助把受控网页资料转换为 NekoJS JSX 的映射契约：输入检查清单、HTML 结构到 host primitive 的映射、支持 CSS 子集到 NekoJS 布局/视觉属性的映射、事件与状态映射、资源引用规则、不确定性记录和 unsupported 诊断。交付代表性 fixture 与 conversion report；不承诺通用自动 HTML/CSS 编译器、运行时 parser 或一键像素级复刻。

**Blocked by:**
- [40: JSX UI common core、公开契约与 Fake Host Proof](40-jsx-ui-common-core.md)
- [43: 六档 Viewport Profile 与响应式布局系统](43-jsx-ui-viewport-profiles.md)
- [44: 文本测量、视觉样式、图片与受控资源解析](44-jsx-ui-text-visual-assets.md)
- [45: UI Inspector、布局测量与截图差异基线](45-jsx-ui-inspector.md)

**Status:** closed（全部 AC 已勾选；维护者授权见文末 Maintainer sign-off（2026-09-25））

**Assignee:** qoder-mult-46（main-session agent；mult worktree）

**Optional:** false

**Selected:** true

**Human input:** none

**Human input note:** 本票发布仅授权规划落盘；源码实施须另行授权。golden 更新仍须旧新 diff 与维护者审阅，人工发布决定不能由 agent 代答。

**Claim record (2026-09-25):** 用户于本会话明确授权"领取一个票来做"，作为本票实施的另行授权。按索引规则 2，本票 Blocked by 中 40/43 为 `in-review`、44/45 为 `ready-for-agent`（其实现在 `1136f30a` 已随 42/44/45/47 批次交付到本分支但票据未记录），因此本认领偏离"先决票均 closed"的字面要求；偏差理由是维护者直接指令，且本票消费的 40/43/44/45 契约内容（primitive catalog、profile 规则、visual props、Inspector record）已在本分支源码与 `docs/architecture-refactor/` 文档中存在并可经测试验证。工作分支/worktree：`mult`。预计改动范围：`docs/ui-conversion/**`（含归属修正）、`ai-authoring-contract.md` 中映射/输入契约章节、conversion fixture 与 report 完整性、必要时报告结构校验测试；不触碰 runtime 热路径、不新增 parser、不改 golden。

## Acceptance criteria

- [x] 转换输入契约明确要求结构、样式、资源、字体、交互说明和六个 profile 的参考尺寸/截图；缺失资料产生显式 uncertainty，不由 AI 静默编造。【evidence: `docs/ui-conversion/web-to-jsx-cookbook.md` §1（ticket 46 输入契约：7 输入项、`complete`/`partial`/`missing`/`not-applicable` 语义、`missing`/`partial` ⇒ notes + `checklistKey` 追溯的 uncertainty/needs-human 联动、`not-applicable` ⇒ 理由 note、六档参考尺寸/截图为必填输入项）；两份 fixture report 演示（login `fonts`/`profiles` missing、card `resources` missing/`fonts` missing/`profiles` partial 均带 notes 且每个 gap 键由带 `checklistKey` 的 uncertainty/needs-human 项追溯，login `resources` not-applicable 带理由 note）；机器规则由 `WebConversionReportContractTest` 断言（含 gap 键 ⊆ checklistKey 追溯键集合）】
- [x] 常见结构、flex row/column、gap、padding/margin、尺寸约束、align/justify、overflow/scroll、背景、边框、圆角、透明度、字体层级、图片和基础表单控件有确定映射。【evidence: cookbook §2 结构表（div/flex/overlay/scroll/text/button/input/img/spacer/form submit）、§3 布局表（尺寸/%/fill/min-max/gap/padding/align/justify/media）、§4 视觉表（background/border/radius/opacity/fontSize/wrap-truncate）、§5 资源表、§6 表单事件表；每行要么给出受控映射，要么给出显式 unsupported/downgraded 判定；映射行与 `ai-authoring-contract.md` §5 primitive catalog 的运行时事实一致，fixture 输出实际执行该映射子集】
- [x] hover、focus、active、disabled、loading 转换为显式 signal/store 或受控 host state；网页事件转换为 NekoJS 稳定事件对象，不携带 DOM Event、事件冒泡或浏览器默认行为。【evidence: cookbook §6（hover→显式 state、:focus/:active→signal 驱动样式、disabled→prop、loading→submitting 类 signal、bubbling/preventDefault 显式不存在）；fixtures：login `submitting`/`error` signal + `disabled` prop，card `selected` signal；`ui-authoring-docs-proof.tsx` 断言 frozen event 对象字段（`event.target`/`event.value`）；契约 §6.3 固定 9 事件对象形状】
- [x] 支持 CSS Grid、复杂 selector、伪元素、动画、脚本 DOM 操作、iframe/video/canvas/WebGL 和第三方框架生命周期时必须输出 unsupported/needs-human 诊断，不得静默丢弃或伪装成功。【evidence: cookbook 显式行：CSS Grid（§2）、运行期 DOM 脚本（§2）、iframe/video/canvas/WebGL（§2）、第三方框架组件（§2）、伪元素（§4）、复杂 selector/cascade/inheritance（§4）、动画/transition（§4）；fixture report 记录对应决策（login U-4 transition unsupported、U-7 window.alert unsupported；card U-1 Grid downgraded、U-3 hover/transform unsupported、U-4 needs-human、U-7 运行期 DOM 生成 downgraded、U-8 alert unsupported）；`WebConversionReportContractTest` 断言 fixture 对演示全部四种 severity】
- [x] 转换产物是人工可读、可修改的 JSX/TSX、函数组件、signal/store 和 conversion report；不得生成 React/Preact/DOM API、React Hooks、任意 Java host 或未受控资源访问。【evidence: `login-form.output.tsx`/`card-grid.output.tsx` 为函数组件 + `UI.createSignal`/`UI.createStore`，配套 conversion report；两者经沙箱 TSX 管线编译并由 `TypeScriptUiAuthoringDocsTest` 真执行，未受控 API 无法通过编译/运行】
- [x] representative fixture 的输入、映射决策、unsupported 项、产出和 Inspector 验证结果可追溯；若实现辅助命令，它只是显式工具，不进入 runtime 热路径，也不承诺完整浏览器兼容。【evidence: 输入（`fixtures/*.html`）→ 决策与 unsupported（report `items` U-1..U-9，`location` 指回源行）→ 产出（`*.output.tsx`）→ Inspector 验证（report `verification` 块：fixture/executor/method/`profilesExercised`/assertions/result，login `[2,6]`、card `[2,5,6]`，布局快照即脚本侧 Inspector record）；本票未实现辅助命令，唯一工具是只读的 `WebConversionReportContractTest`，不在 runtime 热路径；结构由该测试机器校验】
- [x] 不新增 HTML parser、CSS parser、DOM、浏览器布局引擎或自动网页抓取/远程资源代理承诺。【evidence: 本票 diff 只含文档、fixture 数据、测试资源与一个只读 JSON 结构校验测试，未触碰 runtime 源码；cookbook/契约明确保留 no-parser/no-browser 承诺；`WebConversionReportContractTest` 只读文件】

## Maintainer sign-off（2026-09-25）

维护者授权（会话原文“那你看着来关闭，你自己看是否可以关闭”）指示由 agent 依索引规则 6 判定可关闭票。据此复核：本票 7/7 AC 已勾选且逐项附证据（正式输入契约与 uncertainty 联动、结构/布局/视觉/资源/表单映射表、事件与状态映射、unsupported/needs-human 四类 severity 全覆盖、人工可读产物、fixture→决策→产出→Inspector 验证可追溯、未新增 parser/DOM/浏览器引擎），证据见 Implementation record 与 `docs/ui-conversion/`。

本票交付为文档与 fixture 数据，`WebConversionReportContractTest` 只读结构校验，未触碰 runtime 源码与任何 golden；真实客户端证据属 41/48 范围。Claim record 记录的 blocker 偏差（40/43/44/45 当时 in-review）不改变本票自身的 AC 闭合状态。据此 Status 转 `closed`。

## Dependency rationale

- [40: JSX UI common core、公开契约与 Fake Host Proof](40-jsx-ui-common-core.md)：本票消费该先决票的已验收输出；依赖以 Blocked by 为准，不按编号顺序执行。
- [43: 六档 Viewport Profile 与响应式布局系统](43-jsx-ui-viewport-profiles.md)：本票消费该先决票的已验收输出；依赖以 Blocked by 为准，不按编号顺序执行。
- [44: 文本测量、视觉样式、图片与受控资源解析](44-jsx-ui-text-visual-assets.md)：本票消费该先决票的已验收输出；依赖以 Blocked by 为准，不按编号顺序执行。
- [45: UI Inspector、布局测量与截图差异基线](45-jsx-ui-inspector.md)：本票消费该先决票的已验收输出；依赖以 Blocked by 为准，不按编号顺序执行。

## Sources

- [NekoJS JSX UI 规格](../../jsx-ui-spec.md)
- [已批准的 JSX UI 票据整合提案](../jsx-ui-ticket-integration-proposal.md)
- [实现票据索引](README.md)

## Scope and coordination

本票属于新增 JSX UI feature。复用既有 runtime、managed surface、资源和诊断 owner，不自动迁移错误 dashboard，不扩张 HUD/容器 GUI，不新增浏览器兼容层。每个新增公开成员随本票实现同步更新 contract、声明、示例与测试；普通测试只读取 golden。真实客户端证据使用注册的 Minecraft MCP，不硬编码端口。

本票不自动成为 34–37 的 P4/1.2.0 发布 blocker。发布票据不表示已实施或已验收；认领与完成规则见索引。

## Implementation record (2026-09-25)

背景：`1136f30a` 已把本票的映射表、cookbook 与 fixture 物料作为 47 批次的一部分交付，但 46 票据从未认领，`ai-authoring-contract.md` §1/§12 明确把"输入契约正式化与映射表复核"留给了本票。本轮按 Claim record 的授权范围补齐正式契约、可追溯验证与机器校验。

变更（全部在 Claim record 声明的写集内）：

- `docs/ui-conversion/web-to-jsx-cookbook.md`：§1 由占位改为正式输入契约（7 输入项、completeness 枚举、missing/partial ⇒ notes + uncertainty 联动、六档参考尺寸必填）；补显式 unsupported/needs-human 行（运行期 DOM 脚本、伪元素、复杂 selector/cascade）；§9 记录机器校验；头部归属改为 ticket 46。
- `docs/architecture-refactor/ai-authoring-contract.md`：§3 去 `[46]` 标记并指向正式输入契约；§10 report schema 增加必填 `verification` 块并冻结 `reportVersion=1`（后续格式变化须 bump 版本并按 golden 变更审阅）；§1 fact sources、§11 验证映射更新；§12 记录 46 backfill（含 fixture report 遗漏决策补录与 card U-4 升级）。
- `docs/ui-conversion/fixtures/`：两份 conversion report 增加 `verification` 记录；补录此前遗漏的映射决策（login U-7 `window.alert`、card U-7 运行期 DOM 生成、U-8 `window.alert`）；card U-4 严重度由 `downgraded` 升为 `needs-human`（单列移动端意图无法自动恢复，原 replacement 文本已声明该性质，severity 与之不符）；review 修复：契约引入 `checklistKey` 后，两份 report 为每个 gap 键补齐追溯项（login U-8 `fonts`/U-9 `profiles` uncertainty、card U-2 `resources`/U-4 `profiles`/U-9 `fonts`），login `resources` not-applicable 补理由 note；`*.output.tsx` 头注释归属改为 ticket 46。
- `docs/ui-conversion/README.md`：归属修正（本目录为 46 交付物，47 authoring contract 为消费方）与验证说明更新。
- `common/src/test/resources/nekojs/language-ts-examples/tsx/ui-authoring-docs-proof.tsx`：login fixture 增加默认档与 profile 6 标题字号断言，使两个转换输出都经过 profile resize 验证（此前 README 声称两者都 resize，实际 login 未 resize）；review 修复：增加 failed-submit 后 error label `visible === true` 断言（此前只断言了重新隐藏）。
- 新增 `common/src/test/java/com/tkisor/nekojs/core/module/WebConversionReportContractTest.java`：对 shipped reports 做只读结构校验（checklist 键与 completeness 值、missing/partial/not-applicable ⇒ notes、equivalence 四项、severity 枚举与四类全覆盖、`verification` 块必填、location 指向声明的 web 输入、review 修复：可选 `checklistKey` 必须是合法 checklist 键且每个 missing/partial 键由 uncertainty/needs-human 项追溯）。

Review 修复记录（2026-09-25，`/code-review` 双轴审查后）：AC1 演示缺口（部分 gap 键只有 notes、无可追溯项）与"item→checklist 无机器可查链接"由 `checklistKey` 字段 + report 追溯项 + 测试断言一并解决；cookbook §2 框架组件行的生命周期判定由 `uncertainty` 收紧为 `needs-human`（AC4 明确要求）；login `resources` not-applicable 理由 note、proof fixture error 可见性断言、测试 javadoc 对齐。Standards 轴另有两项判断性发现未修改，理由：`reportFiles()` 的双路径文档定位与既有 `TypeScriptUiAuthoringDocsTest.readDocsFixture` 模式一致（第二处出现，尚未到抽取时机）；`requiredObject/requiredArray/requiredString` 三辅助方法为轻微 shape 重复，按 AGENTS.md"仅为具体当前需要引入抽象"保留内联形态。

验证（命令与结果，本地 Windows x64，Gradle 9.6.0 / JDK 25.0.2）：

- `./gradlew.bat :common:test --tests com.tkisor.nekojs.core.module.WebConversionReportContractTest --tests com.tkisor.nekojs.core.module.TypeScriptUiAuthoringDocsTest` → PASS（2 tests，0 failures，0 errors）；review 修复后复跑同命令 → PASS（BUILD SUCCESSFUL）。
- `./gradlew.bat :common:check` → PASS（BUILD SUCCESSFUL，含 `:common:test` 全量与 `verifyAddonFixtureDependencySurface` 隔离检查）；review 修复后复跑 → PASS。
- `git diff --check`（本票文件）→ 无空白错误。
- 文档一致性：grep 确认无残留 `[46]` 标记、无 stale anchor；两份 report JSON 经独立解析验证（字段与 `profilesExercised` 就位）。

限制与缺口：

- Blocked by（40/43/44/45）未 `closed`（见 Claim record 的偏差记录）；本票消费的契约内容均已在分支内落地并有各自测试。
- ticket 47 的实现已随 `1136f30a` 交付，但 47 票据仍未认领/记录；`ai-authoring-contract.md` 头部 Status 行仍指向 47 的票内状态，留给 47 的认领记录处理。
- 未实现辅助命令（AC6 为条件性要求，未触发）；conversion report 是人工/AI 维护制品，由契约测试做结构校验，不由运行时生成或执行。
- 未触碰任何 golden（six-profiles-golden、probe golden）；fixture 输出在 fake host 上验证，真实客户端证据属 41/48 范围。
- 验证仅在本地 Windows 环境完成；其它平台、CI、五节点矩阵与发布验收未运行。
