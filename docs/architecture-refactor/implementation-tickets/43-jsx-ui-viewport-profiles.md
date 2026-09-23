# 43: 六档 Viewport Profile 与响应式布局系统

**What to build:** 在 common 布局模型中实现 NekoJS 自己的 Viewport Profile 1–6 判定和响应式属性解析：由实际逻辑视口、安全区域和布局能力选择 profile，支持连续比例、逻辑像素、`fill`、`auto`、百分比、min/max、间距、内边距、对齐、锚定、方向切换、可见性和字号覆盖。窗口 resize/profile 变化触发 measure/arrange 失效，但不触发每帧脚本 render。

**Blocked by:**
- [40: JSX UI common core、公开契约与 Fake Host Proof](40-jsx-ui-common-core.md)

**Status:** in-review

**Assignee:** 维护者/执行者：sol-ticket43（pixelstarrysky/gpt-6-sol xhigh）

**Claim record (2026-09-22):** branch `ticket-43-jsx-ui-viewport-profiles`, worktree `D:\mcmodDemo\NekoJS-mult-t43`, baseline `aa30e82f`. Expected write set: ticket 43 metadata/acceptance, common public viewport/layout contract and implementation, fake Adapter fixtures/golden evidence, focused common tests; no NeoForge Screen/input, visual assets, Inspector, or web conversion.

**Optional:** false

**Selected:** true

**Human input:** none

**Human input note:** 本票发布仅授权规划落盘；源码实施须另行授权。golden 更新仍须旧新 diff 与维护者审阅，人工发布决定不能由 agent 代答。

## Acceptance criteria

- [x] Profile 1–6 的判定规则、边界、优先级、tie-break 和可观察输出进入公开 contract；不把 Minecraft GUI scale 数字等同 Profile，只允许原生 scale 作为输入之一。【evidence: `UI.profileFor` uses post-safe-area content width/height tiers (320/480/640/854/1280 and 180/240/360/480/720), selects the lower axis tier as the tie-break, then applies `maxProfile`; fixture asserts six boundaries, GUI-scale independence, capability cap, zero rejection】
- [ ] 基础值与 profile 覆盖值的解析顺序、缺省回退、非法 profile、非法比例和 min/max 裁剪规则固定，并由 fake Adapter golden 覆盖。【evidence: deterministic fake fixture covers profile fallback, invalid values, percentage/min/max and diagnostics; no golden file was regenerated or maintainer-reviewed in this ticket】【evidence: profile override resolution checks exact profile then lower profile then base; invalid profile keys and finite/positive viewport inputs fail; fixture observes base/profile percentage, min/max, overflow and invalid-profile/responsive-minmax diagnostics】
- [x] row/column、stack、scroll、spacing、padding、align、anchor、可见性和文本规格可按 profile 覆盖；窄屏/宽屏重排不需要每个属性重复填写六遍。【evidence: deterministic fake layout snapshot asserts responsive row direction/gap, stack anchor, scroll offset/clipping, padding, profile visibility and text-size override】
- [x] 设计坐标、逻辑像素和连续比例可共存；文本字号和可读性相关属性不被强制整体等比无限缩放。【evidence: public viewport contract exposes logical/design coordinate spaces and design scale; resolver accepts logical percentages and design dimensions; fake snapshot keeps font-size as an explicit profile value】
- [x] resize/profile 切换只使布局失效并重新 measure/arrange，不重新执行 GraalJS render 或重建全部 host node；稳定输出包含最终矩形、裁剪和溢出诊断。【evidence: fake Adapter records layout snapshot, final rect/clip/overflow; two resize calls increment layout only, with unchanged render and commit counts】
- [ ] 六个 profile 均有稳定 fake 输出；真实 NeoForge 26.2 resize smoke 在 41 完成后补入同一公开 contract，不用私有 widget 布局作为断言。【not run: real NeoForge 26.2 resize smoke is owned by ticket 41/client evidence; no human sign-off is authored here】


## Delivery record (2026-09-23)

- Status: `in-review`; AC1 and AC3–AC5 are technically evidenced above. AC2 remains unchecked pending a maintainer-reviewed golden; AC6 remains unchecked because real NeoForge 26.2 resize smoke was not run.
- Public contract: `NekoUiProfile`, `NekoUiViewportInput`, `NekoUiViewport`, responsive props, `NekoUiLayoutSnapshot`, `NekoUiRootHandle.resize/layout`, and optional Adapter text measurement are sourced from `common/src/main/resources/nekojs/node/modules/jsx-runtime.ts`; declarations remain auto-extracted from that source.
- Profile rule: safe-area-adjusted content dimensions choose the minimum width/height tier; exact thresholds are deterministic, lower tier wins ties, `maxProfile` caps the result, and `guiScale` is metadata/input only. Profile overrides resolve exact profile, then lower profiles, then `base`; malformed keys/values fail at the common boundary.
- Fake evidence: `ui-core.tsx` covers all six boundaries, overrides, percentage/min/max, direction/spacing/padding, stack/anchor, scroll/clip, visibility/font-size, final rectangles/clips/overflow, resize without render/host rebuild, cross-Fragment duplicate keys, close commit failure/retry, and two queued `update(v => v + 1)` calls that produce `+2` only after owner flush.
- Passed: `./gradlew.bat :common:test --tests com.tkisor.nekojs.core.module.NekoTypeScriptJsxRuntimeTest --tests com.tkisor.nekojs.core.compiler.NodeModuleTypeDocsTest`; `./gradlew.bat :common:check guardLint`; `git diff --check`.
- Passed after `npm ci`: `npm run test:probe-types` (TypeScript 5.8.3, exit 0). Not run: golden regeneration/review, real NeoForge 26.2 resize smoke, and maintainer acceptance. No golden files were changed.
- Shared runtime hardening included in this ticket: cross-Fragment sibling-key rejection, retry-safe close on Adapter commit failure, and owner-thread-first signal updater execution. These are common-runtime evidence only and do not sign or close ticket 40.

## Dependency rationale

- [40: JSX UI common core、公开契约与 Fake Host Proof](40-jsx-ui-common-core.md)：本票消费该先决票的已验收输出；依赖以 Blocked by 为准，不按编号顺序执行。

## Sources

- [NekoJS JSX UI 规格](../../jsx-ui-spec.md)
- [已批准的 JSX UI 票据整合提案](../jsx-ui-ticket-integration-proposal.md)
- [实现票据索引](README.md)

## Scope and coordination

本票属于新增 JSX UI feature。复用既有 runtime、managed surface、资源和诊断 owner，不自动迁移错误 dashboard，不扩张 HUD/容器 GUI，不新增浏览器兼容层。每个新增公开成员随本票实现同步更新 contract、声明、示例与测试；普通测试只读取 golden。真实客户端证据使用注册的 Minecraft MCP，不硬编码端口。

本票不自动成为 34–37 的 P4/1.2.0 发布 blocker。发布票据不表示已实施或已验收；认领与完成规则见索引。
